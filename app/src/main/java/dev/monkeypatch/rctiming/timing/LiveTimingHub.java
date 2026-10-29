package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.battle.BattleGroupDto;
import dev.monkeypatch.rctiming.timing.dto.DecoderStatusDto;
import dev.monkeypatch.rctiming.timing.dto.FeaturedDriverDto;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import dev.monkeypatch.rctiming.timing.dto.LiveFeedStatusDto;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import dev.monkeypatch.rctiming.timing.dto.RaceStateChangeDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * STOMP broadcast hub. Broadcasts on these topics:
 * - /topic/race/{raceId}/timing — live position updates
 * - /topic/race/{raceId}/battles — cars running close together and whether each gap is closing or growing
 * - /topic/race/{raceId}/state  — race state transitions
 * - /topic/race/{raceId}/featured — the race director's featured-driver lower third for the overlay (#533)
 * - /topic/race/{raceId}/marshal — marshal adjustments
 * - /topic/race/{raceId}/unknown-transponder — first sighting of unknown transponder
 * - /topic/race/{raceId}/jump-start — cars that crossed the line before their turn in a stagger start
 * - /topic/race/{raceId}/warmup — cars of a race on the grid the decoder has seen so far
 * - /topic/race/{raceId}/bump-up-alert — drivers promoted when a lower final finishes
 * - /topic/race/{raceId}/audio — spoken running orders and auto run's start cues, for the announcer's browser
 * - /topic/event/{eventId}/auto-run — an event's auto run state, for open race control pages
 * - /topic/system/decoder-status and /topic/system/live-feed-status — connection status
 */
@Component
public class LiveTimingHub {

    private final SimpMessagingTemplate messagingTemplate;

    public LiveTimingHub(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends the race's whole position table, not a delta, so a client that misses one message is right again at
     * the next. Sent at once, not after a commit: live positions live in memory and nothing is stored. Called from
     * the timing thread for each passing and from request threads for marshal laps, penalties and transponder links.
     */
    public void broadcastTimingUpdate(long raceId, List<LiveTimingRowDto> rows) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/timing", rows);
    }

    /**
     * Sends the race's current battles (#505), the whole list each time, empty when no cars are close, so a client that
     * misses a message is right again at the next and a battle that ends clears. Anonymous clients may subscribe, like
     * the timing topic. Sent at once, from the same callers as {@link #broadcastTimingUpdate}.
     */
    public void broadcastBattles(long raceId, List<BattleGroupDto> battles) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/battles", battles);
    }

    /**
     * Tells the streaming overlay to show a lower third for one car (#533): {@code /topic/race/{raceId}/featured} carries
     * the entry id only, and the overlay already holds the name, car number, headshot and sponsors from the public
     * board endpoints (which leave out a hidden name). Anonymous clients may subscribe, like the timing topic. A
     * one-off message, not state: a client that misses it shows nothing. Sent at once, from a request thread.
     */
    public void broadcastFeaturedDriver(long raceId, long entryId) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/featured", new FeaturedDriverDto(raceId, entryId));
    }

    /**
     * Sent once the change commits: boards refetch on this message from the read pool, which only sees
     * committed data, so sent earlier they would fetch the race as it was. Nothing is sent on a rollback.
     */
    public void broadcastStateChange(long raceId, RaceStatus newStatus) {
        sendAfterCommit("/topic/race/" + raceId + "/state", new RaceStateChangeDto(raceId, newStatus.name()));
    }

    /** Tells the race director's screens a marshal lap was given or taken away. Sent at once. */
    public void broadcastMarshalAdjustment(long raceId, MarshalAdjustmentDto dto) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/marshal", dto);
    }

    /**
     * Prompts the referee to link a transponder that matched no entry, or more than one, in the running race.
     * Sent once per number per race, from the timing thread.
     */
    public void broadcastUnknownTransponder(long raceId, String transponderNumber) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/unknown-transponder",
                (Object) Map.of("raceId", raceId, "transponderNumber", transponderNumber));
    }

    /**
     * Tells race control which entries of a race on the grid the decoder has seen on the warm-up lap (#482). Sends the
     * whole list, not a delta, so a screen that misses one message is right again at the next. Sent at once, from the
     * timing thread.
     */
    public void broadcastWarmUp(long raceId, List<Long> seenEntryIds) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/warmup",
                (Object) Map.of("raceId", raceId, "seenEntryIds", seenEntryIds));
    }

    /**
     * Tells the referee which cars have jumped a stagger start. Sends the whole list, not a delta, so a screen that
     * misses one message is right again at the next. Sent at once, from the timing thread.
     */
    public void broadcastJumpStarts(long raceId, List<JumpStartDto> jumpStarts) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/jump-start", jumpStarts);
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

    /**
     * Sends a cue for the announcer's browser to speak or play: a running-order line or a start countdown. Sent at
     * once, not after a commit, because a cue is only ever heard when it is sent and describes nothing stored. Called
     * from the running-order check and from auto run's scheduler thread.
     */
    public void broadcastAudioCue(long raceId, Object cue) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/audio", cue);
    }

    /**
     * Sends an event's auto run state to open race control pages. Sent at once; the state is rebuilt from memory and
     * a page that misses one is right again at the next. Called from request threads and auto run's scheduler thread.
     */
    public void broadcastAutoRunState(long eventId, Object state) {
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/auto-run", state);
    }

    /** The live feed's connection to the relay, for the race-control status bar (#28). */
    public void broadcastLiveFeedStatus(LiveFeedStatusDto status) {
        messagingTemplate.convertAndSend("/topic/system/live-feed-status", status);
    }

    /**
     * Sends when the current transaction commits, or now outside one. Callers are race-control commands in a
     * write transaction.
     */
    private void sendAfterCommit(String destination, Object payload) {
        AfterCommit.run(() -> messagingTemplate.convertAndSend(destination, payload));
    }
}
