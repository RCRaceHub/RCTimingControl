package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.timing.dto.RaceStateChangeDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** A race's new state goes out once it commits, so boards that refetch on it read the new state. */
class LiveTimingHubTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final LiveTimingHub hub = new LiveTimingHub(messaging);

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void outsideATransaction_theStateIsSentAtOnce() {
        hub.broadcastStateChange(7, RaceStatus.FINISHED);

        verify(messaging).convertAndSend("/topic/race/7/state", new RaceStateChangeDto(7, "FINISHED"));
    }

    @Test
    void anAudioCueAndAnAutoRunStateGoToTheirTopicsAtOnce() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        Map<String, Object> cue = Map.of("type", "start-countdown", "seconds", 10L);

        hub.broadcastAudioCue(7, cue);
        hub.broadcastAutoRunState(3, "state");

        verify(messaging).convertAndSend("/topic/race/7/audio", (Object) cue);
        verify(messaging).convertAndSend("/topic/event/3/auto-run", "state");
    }

    @Test
    void insideATransaction_theStateIsSentOnCommitOnly() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        hub.broadcastStateChange(7, RaceStatus.FINISHED);
        hub.broadcastBumpUpAlert(7, List.of(3L));
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(messaging).convertAndSend("/topic/race/7/state", new RaceStateChangeDto(7, "FINISHED"));
        verify(messaging).convertAndSend("/topic/race/7/bump-up-alert",
                (Object) Map.of("finishedRaceId", 7L, "promotedEntryIds", List.of(3L)));
    }

    @Test
    void onARollback_nothingIsSent() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        hub.broadcastStateChange(7, RaceStatus.FINISHED);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }
}
