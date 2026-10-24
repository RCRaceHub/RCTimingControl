package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Finishes a timed race that has gone quiet after its clock ran out (TRACK-03), so a broken car on the track cannot
 * hold the finish up for ever. The track's "max last lap" for the race's class is the longest it waits: once the
 * clock has expired, the race is finished when no lap has counted for that long, counted from the moment the
 * clock expired if none has counted since. A race with no length is judged by lap count instead, from the first car
 * to take the flag. A race with no track or no max last lap is left for the race director.
 *
 * <p>A format can also set a maximum race window, a hard limit on how long the race runs whatever the cars are doing
 * (the BRCA 6:30 cut-off). When it is reached the race is finished at once, and a car that is still on its last lap
 * keeps only the laps it had completed, so it does not score the lap it did not finish.
 *
 * <p>It finishes the race through {@link AutoFinish}, as {@code system:last-lap-timeout}. This is the backstop: the
 * race normally finishes sooner, once every car has taken the flag. Only the running race is looked at: a stopped
 * race is paused, and its clock is not expiring.
 */
@Component
public class LastLapTimeout {

    private static final Logger log = LoggerFactory.getLogger(LastLapTimeout.class);

    private final RaceRepository raceRepository;
    private final RaceClockService raceClockService;
    private final LapTimingService lapTimingService;
    private final LapThresholdQuery lapThresholdQuery;
    private final AutoFinish autoFinish;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;

    public LastLapTimeout(RaceRepository raceRepository,
                          RaceClockService raceClockService, LapTimingService lapTimingService,
                          LapThresholdQuery lapThresholdQuery, AutoFinish autoFinish,
                          EventClassRepository eventClassRepository, RaceFormatService raceFormatService) {
        this.raceRepository = raceRepository;
        this.raceClockService = raceClockService;
        this.lapTimingService = lapTimingService;
        this.lapThresholdQuery = lapThresholdQuery;
        this.autoFinish = autoFinish;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
    }

    /**
     * Whether the maximum race window has been reached.
     *
     * @param overrunMs the time since the clock expired, counted from the last car's start
     * @param durationMs the race's length
     * @param windowMs   the maximum race window, from the start of the race
     */
    static boolean windowReached(long overrunMs, long durationMs, long windowMs) {
        // A window shorter than the race itself behaves as no extra time after the clock
        return overrunMs >= Math.max(0, windowMs - durationMs);
    }

    /**
     * Whether the race has waited long enough.
     *
     * @param overrunMs  how long ago the clock expired
     * @param quietMs    how long since a lap last counted, or null if none has
     * @param maxLastLapMs the longest to wait
     */
    static boolean timedOut(long overrunMs, Long quietMs, long maxLastLapMs) {
        long idleMs = quietMs == null ? overrunMs : Math.min(overrunMs, quietMs);
        return idleMs > maxLastLapMs;
    }

    /**
     * Looks at the running race once a second, on the scheduler thread. Does nothing while the clock is still
     * running. Any failure is logged and the next tick tries again, so a database blip cannot stop the backstop.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 1_000)
    public void checkRunningRace() {
        try {
            raceRepository.findFirstByStatus(RaceStatus.RUNNING).ifPresent(this::checkRace);
        } catch (RuntimeException e) {
            log.warn("Could not check the last lap timeout; will try again", e);
        }
    }

    /**
     * How long until this timeout or the maximum race window would finish the running race, for the countdown on
     * the cockpit and referee screens. It uses the same figures as {@link #checkRace}: once the clock has run out
     * (or, for a lap-count race, the first car has taken the flag) the race closes when no lap has counted for the
     * track's max last lap, or when the window is reached, whichever is first. A lap that counts puts the first
     * back, so the answer is read fresh rather than counted on by the viewer for long.
     *
     * <p>Runs on a request thread and only reads. Empty when the race is not running, its clock has not run out,
     * or neither limit applies, so the screen shows nothing.
     *
     * @return milliseconds left, never below 0
     */
    public Optional<Long> closesInMs(long raceId) {
        Optional<Race> found = raceRepository.findById(raceId).filter(r -> r.getStatus() == RaceStatus.RUNNING);
        Optional<RaceClockDto> clockFound = found.flatMap(r -> raceClockService.clock(r.getId()));
        if (clockFound.isEmpty()) {
            return Optional.empty();
        }
        Race race = found.get();
        RaceClockDto clock = clockFound.get();
        Optional<LiveRaceState> state = lapTimingService.peek(raceId);
        Optional<Long> maxLastLapMs = lapThresholdQuery.maxLastLapMsForRace(raceId);
        long now = System.currentTimeMillis();
        Long quietMs = state.flatMap(LiveRaceState::lastLapCreditedAtMs).map(at -> now - at).orElse(null);
        if (clock.durationMs() == null) {
            Optional<Long> firstFlaggedAtMs = state.flatMap(LiveRaceState::firstFlaggedAtMs);
            if (firstFlaggedAtMs.isEmpty() || maxLastLapMs.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(waitLeftMs(now - firstFlaggedAtMs.get(), quietMs, maxLastLapMs.get()));
        }
        if (clock.remainingMs() > 0) {
            return Optional.empty();
        }
        long lastStartMs = state.map(LiveRaceState::lastStartOffsetMs).orElse(0L);
        long overrunMs = clock.elapsedMs() - clock.durationMs() - lastStartMs;
        if (overrunMs < 0) {
            return Optional.empty();
        }
        Optional<Long> windowMs = race.getEventClassId() == null ? Optional.empty()
                : eventClassRepository.findById(race.getEventClassId()).flatMap(raceFormatService::maxRaceWindowMs);
        Optional<Long> byWindow = windowMs.map(w -> Math.max(0, Math.max(0, w - clock.durationMs()) - overrunMs));
        Optional<Long> byQuiet = maxLastLapMs.map(max -> waitLeftMs(overrunMs, quietMs, max));
        return byWindow.isPresent() && byQuiet.isPresent() ? Optional.of(Math.min(byWindow.get(), byQuiet.get()))
                : byWindow.isPresent() ? byWindow : byQuiet;
    }

    /** What is left of the wait that {@link #timedOut} judges, never below 0. */
    static long waitLeftMs(long overrunMs, Long quietMs, long maxLastLapMs) {
        long idleMs = quietMs == null ? overrunMs : Math.min(overrunMs, quietMs);
        return Math.max(0, maxLastLapMs - idleMs);
    }

    private void checkRace(Race race) {
        Optional<RaceClockDto> found = raceClockService.clock(race.getId());
        if (found.isPresent() && found.get().durationMs() == null) {
            checkLapCountRace(race);
            return;
        }
        if (found.isEmpty() || found.get().remainingMs() > 0) {
            return;
        }
        RaceClockDto clock = found.get();
        // The last car to start has the longest window; a stagger start ends later than the clock says
        long lastStartMs = lapTimingService.peek(race.getId()).map(LiveRaceState::lastStartOffsetMs).orElse(0L);
        long overrunMs = clock.elapsedMs() - clock.durationMs() - lastStartMs;
        if (overrunMs >= 0 && lapTimingService.peek(race.getId()).map(LiveRaceState::allFlagged).orElse(false)) {
            // The finish asked for when the last car took the flag may not have happened, say if the race was stopped
            autoFinish.finish(race.getId(), "flag-finish", "Finished the race automatically: every car took the flag");
            return;
        }
        Optional<Long> windowMs = race.getEventClassId() == null ? Optional.empty()
                : eventClassRepository.findById(race.getEventClassId()).flatMap(raceFormatService::maxRaceWindowMs);
        if (windowMs.isPresent() && windowReached(overrunMs, clock.durationMs(), windowMs.get())) {
            // Cars still on their last lap are labelled DNF in the result (#428)
            lapTimingService.peek(race.getId()).ifPresent(LiveRaceState::markCutOff);
            autoFinish.finish(race.getId(), "race-window", "Finished the race automatically: the maximum race window of "
                    + windowMs.get() / 1000 + " seconds was reached, so cars still on their last lap keep the laps they had");
            return;
        }
        Optional<Long> maxLastLapMs = lapThresholdQuery.maxLastLapMsForRace(race.getId());
        if (maxLastLapMs.isEmpty()) {
            return;
        }
        Long quietMs = lapTimingService.peek(race.getId())
                .flatMap(LiveRaceState::lastLapCreditedAtMs)
                .map(at -> System.currentTimeMillis() - at)
                .orElse(null);
        if (timedOut(overrunMs, quietMs, maxLastLapMs.get())) {
            finish(race.getId(), maxLastLapMs.get());
        }
    }

    /**
     * A race of a set number of laps has no clock to run out, so the wait starts when the first car takes the flag:
     * the race is finished when no lap has counted for the max last lap since then. A race with no car flagged yet is
     * left alone, as is one with no max last lap.
     */
    private void checkLapCountRace(Race race) {
        Optional<LiveRaceState> state = lapTimingService.peek(race.getId());
        Optional<Long> firstFlaggedAtMs = state.flatMap(LiveRaceState::firstFlaggedAtMs);
        if (firstFlaggedAtMs.isEmpty()) {
            return;
        }
        Optional<Long> maxLastLapMs = lapThresholdQuery.maxLastLapMsForRace(race.getId());
        if (maxLastLapMs.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long quietMs = state.flatMap(LiveRaceState::lastLapCreditedAtMs).map(at -> now - at).orElse(null);
        if (timedOut(now - firstFlaggedAtMs.get(), quietMs, maxLastLapMs.get())) {
            autoFinish.finish(race.getId(), "last-lap-timeout", "Finished the race automatically: no lap counted for "
                    + maxLastLapMs.get() / 1000 + " seconds after the first car finished");
        }
    }

    private void finish(long raceId, long maxLastLapMs) {
        autoFinish.finish(raceId, "last-lap-timeout", "Finished the race automatically: no lap counted for "
                + maxLastLapMs / 1000 + " seconds after the clock ran out");
    }
}
