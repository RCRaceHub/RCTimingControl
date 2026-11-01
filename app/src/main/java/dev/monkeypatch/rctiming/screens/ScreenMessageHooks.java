package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.event.FinalsPublished;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.service.FinalsLineupService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The two opt-in hooks from race control's event flow to the screens (#716), both off until an official switches them
 * on in the message settings:
 * <ul>
 *   <li>when the last qualifying heat of an event finishes, put up the "Finals being calculated" hold;</li>
 *   <li>when the finals line-ups are confirmed ({@link FinalsPublished}), push the finals playlist and then announce
 *       "Finals lists are up" (the same two steps as the Screens page's button).</li>
 * </ul>
 * Both go to every known screen. The listeners run after the command's transaction commits and only queue the work,
 * which a single background thread then does, so a dead screen can never slow a race command and a failure is logged
 * and never thrown back at race control. What was sent is in the application log and, as the system user, in the audit log.
 */
@Component
public class ScreenMessageHooks {

    private static final Logger log = LoggerFactory.getLogger(ScreenMessageHooks.class);
    private static final Actor ACTOR = Actor.system("screen-messages");

    private final MessageSettingsService settings;
    private final ScreenMessageService messages;
    private final ScreenService screens;
    private final RaceRepository races;
    private final EventClassRepository eventClasses;
    private final FinalsLineupService lineups;
    /** Events whose hold is up, so one finished race after another does not put it back after an official cleared it. */
    private final Set<Long> held = ConcurrentHashMap.newKeySet();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            r -> Thread.ofPlatform().daemon().name("screen-message-hooks").unstarted(r));

    public ScreenMessageHooks(MessageSettingsService settings, ScreenMessageService messages, ScreenService screens,
                              RaceRepository races, EventClassRepository eventClasses, FinalsLineupService lineups) {
        this.settings = settings;
        this.messages = messages;
        this.screens = screens;
        this.races = races;
        this.eventClasses = eventClasses;
        this.lineups = lineups;
    }

    /** A race finishing may be the last qualifying heat; checked on the worker thread, after the commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() == RaceStatus.FINISHED) {
            queue("qualifying hold", () -> holdAfterQualifying(event.getRaceId()));
        }
    }

    /** The finals line-ups being confirmed; handled on the worker thread, after the commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFinalsPublished(FinalsPublished event) {
        queue("finals announcement", () -> announceFinals(event.eventId()));
    }

    /**
     * Puts up the hold when the hook is on and the race that just finished was the last qualifying heat: the finals
     * line-up review is due, which is true only once every qualifying heat is finished and no final has started.
     */
    void holdAfterQualifying(long raceId) {
        MessageSettings.Hooks hooks = settings.get().hooks();
        if (!hooks.holdAfterQualifying()) {
            return;
        }
        Long eventId = races.findById(raceId)
                .map(race -> eventClasses.getOrThrow(race.getEventClassId()).getEventId()).orElse(null);
        if (eventId == null || !lineups.review(eventId).required() || !held.add(eventId)) {
            return;
        }
        List<Long> ids = allScreens();
        if (ids.isEmpty()) {
            return;
        }
        List<ScreenActionResult> results = messages.sendMessage(ACTOR, ids, hooks.holdMessage());
        log.info("Qualifying is over for event {}: put up \"{}\" on {} screen(s), {} failed", eventId,
                hooks.holdMessage().title(), results.size(), failures(results));
    }

    /** Pushes the finals playlist, if one is set, then announces, when the hook is on. */
    void announceFinals(long eventId) {
        held.remove(eventId);
        MessageSettings.Hooks hooks = settings.get().hooks();
        if (!hooks.announceFinals()) {
            return;
        }
        List<Long> ids = allScreens();
        if (ids.isEmpty()) {
            return;
        }
        FinalsUpResult result = messages.finalsUp(ACTOR, existing(hooks.finalsPlaylistId()), ids, hooks.finalsMessage());
        log.info("Finals are up for event {}: playlist {} on {} screen(s), {} failed; announced \"{}\" on {} screen(s), {} failed",
                eventId, hooks.finalsPlaylistId() == null ? "not pushed" : "pushed", result.playlist().size(),
                failures(result.playlist()), hooks.finalsMessage().title(), result.message().size(),
                failures(result.message()));
    }

    /** The playlist id if that playlist still exists, else null so the announcement still goes out. */
    private Long existing(Long playlistId) {
        if (playlistId == null) {
            return null;
        }
        try {
            screens.playlist(playlistId);
            return playlistId;
        } catch (EntityNotFoundException e) {
            log.warn("The saved finals playlist {} no longer exists; announcing without it", playlistId);
            return null;
        }
    }

    private List<Long> allScreens() {
        return screens.screens().stream().map(Screen::getId).toList();
    }

    private static long failures(List<ScreenActionResult> results) {
        return results.stream().filter(r -> !r.ok()).count();
    }

    private void queue(String what, Runnable task) {
        try {
            worker.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.warn("The {} on the screens did not go out: {}", what, e.getMessage(), e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not queue the {} on the screens: {}", what, e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }
}
