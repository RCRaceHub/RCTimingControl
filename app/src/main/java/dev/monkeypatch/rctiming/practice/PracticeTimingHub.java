package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.practice.dto.PracticeTimingRowDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * STOMP broadcast hub for practice sessions.
 * Mirrors LiveTimingHub but uses /topic/practice/{sessionId}/ topics. Messages go out at once, from the timing
 * thread for a passing or a request thread for a link; nothing waits for a commit.
 */
@Component
public class PracticeTimingHub {

    private final SimpMessagingTemplate messagingTemplate;

    public PracticeTimingHub(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcast live timing positions to practice session subscribers.
     * Topic: /topic/practice/{sessionId}/timing
     */
    public void broadcastTimingUpdate(Long sessionId, List<PracticeTimingRowDto> rows) {
        messagingTemplate.convertAndSend("/topic/practice/" + sessionId + "/timing", rows);
    }

    /**
     * Broadcast unknown transponder notifications.
     * Topic: /topic/practice/{sessionId}/unknown-transponder
     */
    public void broadcastUnknownTransponders(Long sessionId, Set<String> transponders) {
        messagingTemplate.convertAndSend("/topic/practice/" + sessionId + "/unknown-transponder", transponders);
    }
}
