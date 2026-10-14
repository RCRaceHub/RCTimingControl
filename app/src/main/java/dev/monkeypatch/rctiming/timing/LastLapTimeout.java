package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * Finishes a timed race that has gone quiet after its clock ran out (TRACK-03), so a broken car on the track cannot
 * hold the finish up for ever. The track's "max last lap" for the race's class is the longest it waits: once the
 * clock has expired, the race is finished when no lap has counted for that long, counted from the moment the
 * clock expired if none has counted since. A race with no length (a fixed number of laps), no track or no
 * max last lap is left for the race director.
 *
 * <p>It finishes the race the way the finish command does, so the result, the next heat's grid and the exports
 * follow, and writes an audit row from {@code system:last-lap-timeout}. Only the running race is looked at: a
 * stopped race is paused, and its clock is not expiring.
 */
@Component
public class LastLapTimeout {

    private static final Logger log = LoggerFactory.getLogger(LastLapTimeout.class);

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final RaceClockService raceClockService;
    private final LapTimingService lapTimingService;
    private final LapThresholdQuery lapThresholdQuery;
    private final RaceStateMachineService stateMachine;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public LastLapTimeout(RaceRepository raceRepository, RoundRepository roundRepository,
                          RaceClockService raceClockService, LapTimingService lapTimingService,
                          LapThresholdQuery lapThresholdQuery, RaceStateMachineService stateMachine,
                          AuditService audit, TransactionTemplate transactions) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.raceClockService = raceClockService;
        this.lapTimingService = lapTimingService;
        this.lapThresholdQuery = lapThresholdQuery;
        this.stateMachine = stateMachine;
        this.audit = audit;
        this.transactions = transactions;
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

    @Scheduled(initialDelay = 5_000, fixedDelay = 1_000)
    public void checkRunningRace() {
        try {
            raceRepository.findFirstByStatus(RaceStatus.RUNNING).ifPresent(this::checkRace);
        } catch (RuntimeException e) {
            log.warn("Could not check the last lap timeout; will try again", e);
        }
    }

    private void checkRace(Race race) {
        Optional<RaceClockDto> found = raceClockService.clock(race.getId());
        if (found.isEmpty() || found.get().durationMs() == null || found.get().remainingMs() > 0) {
            return;
        }
        RaceClockDto clock = found.get();
        long overrunMs = clock.elapsedMs() - clock.durationMs();
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

    private void finish(long raceId, long maxLastLapMs) {
        transactions.executeWithoutResult(status -> {
            // Look again inside the transaction: the director may have finished or restarted it meanwhile
            Race race = raceRepository.findById(raceId).orElse(null);
            if (race == null || race.getStatus() != RaceStatus.RUNNING) {
                return;
            }
            stateMachine.finish(race);
            Long eventId = roundRepository.findById(race.getRoundId()).map(Round::getEventId).orElse(null);
            audit.entry(Actor.system("last-lap-timeout"), "RACE_FINISHED")
                    .entity("race", raceId).race(raceId).event(eventId)
                    .summary("Finished the race automatically: no lap counted for " + maxLastLapMs / 1000
                            + " seconds after the clock ran out")
                    .before(RaceStatus.RUNNING).after(RaceStatus.FINISHED)
                    .record();
            log.info("Race {} finished by the last lap timeout", raceId);
        });
    }
}
