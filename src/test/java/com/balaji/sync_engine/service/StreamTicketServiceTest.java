
package com.balaji.sync_engine.service;

import static org.junit.jupiter.api.Assertions.*;

import com.balaji.sync_engine.exception.InvalidStreamTicketException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamTicketServiceTest {

    private StreamTicketService service;

    @BeforeEach
    void setUp() {
        service = new StreamTicketService();
    }

    @Test
    void redeemReturnsWhoTheTicketWasIssuedTo() {
        String token = service.issue("asha", "dev-1");

        StreamTicketService.Ticket ticket = service.redeem(token);

        assertEquals("asha", ticket.username());
        assertEquals("dev-1", ticket.deviceId());
        assertNotNull(ticket.expiresAt());
    }

    @Test
    void ticketIsSingleUse() {
        String token = service.issue("asha", "dev-1");

        service.redeem(token);

        assertThrows(
            InvalidStreamTicketException.class,
            () -> service.redeem(token)
        );
    }

    @Test
    void unknownTokenIsRejected() {
        assertThrows(
            InvalidStreamTicketException.class,
            () -> service.redeem("nope")
        );
    }

    @Test
    void tokensAreUnique() {
        String first = service.issue("a", "d");
        String second = service.issue("a", "d");

        assertNotEquals(first, second);
    }

    @Test
    void expiredTicketIsRejected() throws Exception {
        String token = service.issue("asha", "dev-1");

        tickets().put(
            token,
            new StreamTicketService.Ticket(
                "asha",
                "dev-1",
                Instant.now().minusSeconds(1)
            )
        );

        assertThrows(
            InvalidStreamTicketException.class,
            () -> service.redeem(token)
        );
    }

    @Test
    void expiredTicketsAreSweptWhenNewOnesAreIssued()
            throws Exception {
        String oldToken = service.issue("asha", "dev-1");

        tickets().put(
            oldToken,
            new StreamTicketService.Ticket(
                "asha",
                "dev-1",
                Instant.now().minusSeconds(1)
            )
        );

        service.issue("rahul", "dev-2");

        assertFalse(tickets().containsKey(oldToken));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, StreamTicketService.Ticket>
            tickets() throws Exception {

        Field field =
            StreamTicketService.class.getDeclaredField("tickets");

        field.setAccessible(true);

        return (ConcurrentHashMap<String, StreamTicketService.Ticket>)
            field.get(service);
    }
}
