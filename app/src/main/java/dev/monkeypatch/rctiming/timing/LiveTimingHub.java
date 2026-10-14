package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.timing.dto.DecoderStatusDto;
import dev.monkeypatch.rctiming.timing.dto.LiveFeedStatusDto;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import dev.monkeypatch.rctiming.timing.dto.RaceStateChangeDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * STOMP broadcast hub. Broadcasts on these topics:
 * - /topic/race/{raceId}/timing — live position updates
 * - /topic/race/{raceId}/state  — race state transitions
 * - /topic/race/{raceId}/marshal — marshal adjustments
 * - /topic/race/{raceId}/unknown-transponder — first sighting of unknown transponder
 * - /topic/race/{raceId}/bump-up-alert — drivers promoted when a lower final finishes
 * - /topic/system/decoder-status and /topic/system/live-feed-status — connection status
 */
@Component
public class LiveTimingHub {

    private final SimpMessagingTemplate messagingTemplate;

    public LiveTimingHub(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastTimingUpdate(long raceId, List<LiveTimingRowDto> rows) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/timing", rows);
    }

    /**
     * Sent once the change commits: boards refetch on this message from the read pool, which only sees
     * committed data, so sent earlier they would fetch the race as it was. Nothing is sent on a rollback.
     */
    public void broadcastStateChange(long raceId, RaceStatus newStatus) {
        sendAfterCommit("/topic/race/" + raceId + "/state", new RaceStateChangeDto(raceId, newStatus.name()));
    }

    public void broadcastMarshalAdjustment(long raceId, MarshalAdjustmentDto dto) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/marshal", dto);
    }

    public void broadcastUnknownTransponder(long raceId, String transponderNumber) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/unknown-transponder",
                Map.of("raceId", raceId, "transponderNumber", transponderNumber));
    }

    /**
     * Broadcasts decoder connection state to /topic/system/decoder-status.
     * The race-control status bar subscribes to this topic.
     */
    public void broadcastDecoderStatus(DecoderStatusDto dto) {
        messagingTemplate.convertAndSend("/topic/system/decoder-status", dto);
    }

    /**
     * Broadcasts a bump-up promotion alert when a lower final finishes.
     * Race director UI subscribes to be notified before starting the next final.
     */
    public void broadcastBumpUpAlert(long finishedRaceId, List<Long> promotedEntryIds) {
        sendAfterCommit("/topic/race/" + finishedRaceId + "/bump-up-alert",
                Map.of("finishedRaceId", finishedRaceId, "promotedEntryIds", promotedEntryIds));
    }

    /** The live feed's connection to the relay, for the race-control status bar (#28). */
    public void broadcastLiveFeedStatus(LiveFeedStatusDto status) {
        messagingTemplate.convertAndSend("/topic/system/live-feed-status", status);
    }

    /**
     * Sends when the current transaction commits, or now outside one. Callers are race-control commands in a
     * write transaction; called from another transaction's after-commit callback the message would be lost.
     */
    private void sendAfterCommit(String destination, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            messagingTemplate.convertAndSend(destination, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSend(destination, payload);
            }
        });
    }
}
