package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.user.OfficialSignedOutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ends an official's live timing sockets when they are disabled or given a new password (#61).
 * <p>
 * A STOMP session is authenticated once, at CONNECT, and can stay open for as long as the page does,
 * so revoking refresh tokens alone would leave it subscribed. This registry keeps every open
 * WebSocket session and the official each one signed in as, and closes them on
 * {@link OfficialSignedOutEvent}. It also remembers when each official was signed out, so a page
 * reconnecting with an access token issued before then is refused at CONNECT.
 * <p>
 * That memory is in-process only: after a restart an access token issued before the sign-out could
 * connect again, but only for what is left of its 15 minutes, and a disabled official is still
 * refused because CONNECT also checks the account.
 */
@Component
public class StompSessionRegistry implements WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(StompSessionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** WebSocket session id → the id of the official it signed in as. */
    private final Map<String, String> officials = new ConcurrentHashMap<>();
    /** Official id → when they were last signed out, to the second like a JWT's issued-at. */
    private final Map<String, Instant> signedOutAt = new ConcurrentHashMap<>();

    /** Tracks each WebSocket session while it is open, so a sign-out can close it. */
    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                officials.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /** Records that the session (the STOMP session id, which is the WebSocket session's) signed in as this official. */
    void signedIn(String sessionId, String officialId) {
        if (sessionId != null) {
            officials.put(sessionId, officialId);
        }
    }

    /**
     * Whether an access token issued at {@code issuedAt} predates the official's last sign-out. A JWT's
     * issued-at is to the second, so a token from the same second as the sign-out counts as before it.
     */
    boolean issuedBeforeSignOut(String officialId, Instant issuedAt) {
        Instant at = signedOutAt.get(officialId);
        return at != null && (issuedAt == null || !issuedAt.isAfter(at));
    }

    /**
     * Closes the official's sockets once the change that signed them out is committed (or at once if it was not in
     * a transaction), and remembers the time so an older access token cannot reconnect.
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void onSignedOut(OfficialSignedOutEvent event) {
        String officialId = String.valueOf(event.userId());
        signedOutAt.put(officialId, event.at().truncatedTo(ChronoUnit.SECONDS));
        officials.forEach((sessionId, signedInAs) -> {
            if (signedInAs.equals(officialId)) {
                close(sessionId);
            }
        });
    }

    private void close(String sessionId) {
        officials.remove(sessionId);
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Signed out"));
        } catch (IOException e) {
            log.debug("Could not close WebSocket session {}: {}", sessionId, e.getMessage());
        }
    }
}
