package com.balaji.sync_engine.service;

import com.balaji.sync_engine.repository.ChangeEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Doorbell: tells connected devices "the log moved, pull". Carries no change data. */
@Component
public class SyncStreamHub {

    private static final Logger log = LoggerFactory.getLogger(SyncStreamHub.class);

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_STREAMS_PER_PRINCIPAL = 5;
    private static final long COALESCE_MS = 200;
    private static final long HEARTBEAT_SECONDS = 25;

    private record StreamConnection(String username, String deviceId, SseEmitter emitter) {}

    private final ChangeEventRepository changeEventRepository;
    private final List<StreamConnection> connections = new CopyOnWriteArrayList<>();

    private final Object lock = new Object();
    private final Set<String> pendingOrigins = new HashSet<>();
    private boolean flushScheduled;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "sync-stream-hub");
        thread.setDaemon(true);
        return thread;
    });


    private final SyncMetrics syncMetrics;


    public SyncStreamHub(ChangeEventRepository changeEventRepository, SyncMetrics syncMetrics) {
        this.changeEventRepository = changeEventRepository;
        this.syncMetrics = syncMetrics;
    }

    @PostConstruct
    void startHeartbeat() {
        scheduler.scheduleWithFixedDelay(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        connections.forEach(c -> completeQuietly(c.emitter()));
    }

    public SseEmitter open(String username, String deviceId) {
        evictOldest(username);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        StreamConnection connection = new StreamConnection(username, deviceId, emitter);
        
        emitter.onCompletion(() -> {
            if (connections.remove(connection)) {
                syncMetrics.sseConnectionClosed();
            }
        });
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> {
            if (connections.remove(connection)) {
                syncMetrics.sseConnectionClosed();
            }
        });
        
        connections.add(connection);
        syncMetrics.sseConnectionOpened();

        send(connection, SseEmitter.event().name("ready").data(headJson()));
        return emitter;
    }

    /** Called after a transaction that appended to the log has committed. Coalesced into one ring. */
    public void changesAppended(String originDeviceId) {
        synchronized (lock) {
            pendingOrigins.add(originDeviceId == null ? "" : originDeviceId);
            if (!flushScheduled) {
                flushScheduled = true;
                scheduler.schedule(this::flush, COALESCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void flush() {
        Set<String> origins;
        synchronized (lock) {
            origins = new HashSet<>(pendingOrigins);
            pendingOrigins.clear();
            flushScheduled = false;
        }
        try {
            if (connections.isEmpty()) {
                return;
            }
            String data = headJson();
            // A device that was the ONLY origin already knows about its own changes.
            String onlyOrigin = origins.size() == 1 ? origins.iterator().next() : null;
            for (StreamConnection connection : connections) {
                if (onlyOrigin != null && onlyOrigin.equals(connection.deviceId())) {
                    continue;
                }
                send(connection, SseEmitter.event().name("changes").data(data));
            }
        } catch (Exception ex) {
            log.warn("Failed to flush live-update notifications", ex);
        }
    }

    private void heartbeat() {
        try {
            for (StreamConnection connection : connections) {
                send(connection, SseEmitter.event().comment("keepalive"));
            }
        } catch (Exception ex) {
            log.warn("Stream heartbeat failed", ex);
        }
    }

    private boolean send(StreamConnection connection, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (connection.emitter()) {
                connection.emitter().send(event);
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            if (connections.remove(connection)) {
                syncMetrics.sseConnectionClosed();
            }
            completeQuietly(connection.emitter());
            return false;
        }
    }

    private void evictOldest(String username) {
        List<StreamConnection> mine = connections.stream()
                .filter(c -> c.username().equals(username)).toList();
        int excess = mine.size() - (MAX_STREAMS_PER_PRINCIPAL - 1);
        for (int i = 0; i < excess; i++) {
            StreamConnection oldest = mine.get(i);
            if (connections.remove(oldest)) {
                syncMetrics.sseConnectionClosed();
            }
            completeQuietly(oldest.emitter());
        }
    }
    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already closed
        }
    }

    private String headJson() {
        return "{\"headSeq\":" + changeEventRepository.findHeadSeq() + "}";
    }
}