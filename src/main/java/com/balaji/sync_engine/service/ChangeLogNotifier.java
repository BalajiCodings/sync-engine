package com.balaji.sync_engine.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Rings the doorbell only after the appending transaction has really committed. */
@Component
public class ChangeLogNotifier {

    private final SyncStreamHub hub;

    public ChangeLogNotifier(SyncStreamHub hub) {
        this.hub = hub;
    }

    public void appended(String originDeviceId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hub.changesAppended(originDeviceId);
                }
            });
        } else {
            hub.changesAppended(originDeviceId);
        }
    }
}