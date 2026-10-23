package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each race's clock from its status changes: it resets when the race is called to the grid, counts while it
 * runs and pauses while it is stopped. The boards, the live feed (#28) and the streaming overlay (#29) read it.
 *
 * <p>A race with a rolling start ({@link StartType#ROLLING}) is the exception to "counts while it runs": when it is
 * started its clock is held at zero until the first car that is entered in the race crosses the line ({@link
 * #onFirstPassing}), and then counts for everyone, so every car's time runs out at the same moment. A resume after a
 * stop does not hold the clock again.
 *
 * <p>Clocks are kept in memory only. A race already under way when the app started gets a clock worked out from
 * its start and finish times the first time it is needed, which counts any stop before then as race time, and a
 * rolling start still waiting for its first car is counted from the start command.
 */
@Component
public class RaceClockService {

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final Clock clock;
    private final Map<Long, RaceClock> clocks = new ConcurrentHashMap<>();

    /** A start time this recent belongs to the start command that is being handled (see {@link #startedJustNow}). */
    private static final long FRESH_START_SECONDS = 5;

    @Autowired
    public RaceClockService(RaceRepository raceRepository, RoundRepository roundRepository,
                            EventClassRepository eventClassRepository, RaceFormatService raceFormatService) {
        this(raceRepository, roundRepository, eventClassRepository, raceFormatService, Clock.systemUTC());
    }

    public RaceClockService(RaceRepository raceRepository, RoundRepository roundRepository,
                            EventClassRepository eventClassRepository, RaceFormatService raceFormatService,
                            Clock clock) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.clock = clock;
    }

    /**
     * Starts, pauses or resets the race's clock to match its new status. Runs synchronously on the thread that
     * published the event, a request thread running the race command. A restart publishes no event, which is why
     * {@link #clockOf} treats {@code PENDING} separately.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        Instant now = clock.instant();
        long raceId = event.getRaceId();
        switch (event.getNewStatus()) {
            case GRID -> clocks.put(raceId, new RaceClock());
            case RUNNING -> {
                // Looked up before touching the clock, so a passing that arrives meanwhile finds it settled
                boolean rolling = isRollingStart(raceId);
                if (!clocks.containsKey(raceId) && startedJustNow(raceId, now)) {
                    // The grid was called before the app started, so this clock was never made: a first start
                    clocks.put(raceId, new RaceClock());
                }
                clockFor(raceId, now).startOrHold(now, rolling);
            }
            case STOPPED, FINISHED -> clockFor(raceId, now).stop(now);
            default -> { }
        }
    }

    /**
     * Starts the clock of a rolling start race that is waiting for its first car: called by {@code LapTimingService}
     * on the timing thread for each passing that resolves to an entry in the race. Does nothing for any other race, or
     * once the clock is counting, so it is cheap to call on every passing.
     */
    public void onFirstPassing(long raceId) {
        RaceClock raceClock = clocks.get(raceId);
        if (raceClock != null) {
            raceClock.onPassing(clock.instant());
        }
    }

    /**
     * Whether the race's start command was given now rather than earlier: {@code RaceStateMachineService} sets the
     * start time just before it publishes the event, so a stored start time within a few seconds means this
     * RUNNING is the first start, while an older one means a resume. Only asked when no clock is kept, that is, when
     * the app started after the grid was called or the race was stopped.
     */
    private boolean startedJustNow(long raceId, Instant now) {
        return raceRepository.findById(raceId)
                .map(race -> race.getStartedAt() != null
                        && Duration.between(race.getStartedAt(), now).abs().toSeconds() < FRESH_START_SECONDS)
                .orElse(false);
    }

    /** Whether the race starts rolling, as {@link #startTypeOf} reads it. */
    private boolean isRollingStart(long raceId) {
        return raceRepository.findById(raceId).map(race -> startTypeOf(race) == StartType.ROLLING).orElse(false);
    }

    /** How the race starts: its own start type, else the format's, as the pre-race readiness does; null if unknown. */
    private StartType startTypeOf(Race race) {
        if (race.getStartType() != null) {
            return race.getStartType();
        }
        EventClass eventClass = race.getEventClassId() == null ? null
                : eventClassRepository.findById(race.getEventClassId()).orElse(null);
        return roundRepository.findById(race.getRoundId())
                .map(round -> raceFormatService.startType(eventClass, round.getType())).orElse(null);
    }

    /**
     * The race's kept clock, or one worked out from its stored times for a race under way before the app
     * started, counting if the stored race is running.
     */
    private RaceClock clockFor(long raceId, Instant now) {
        return clocks.computeIfAbsent(raceId, id -> raceRepository.findById(id)
                .map(race -> {
                    RaceClock fromTimes = RaceClock.fromTimes(race, now);
                    if (race.getStatus() == RaceStatus.RUNNING) {
                        fromTimes.start(now);
                    }
                    return fromTimes;
                })
                .orElseGet(RaceClock::new));
    }

    /**
     * The race's clock with its length, or empty for an unknown race. On the write connection, not the read pool,
     * so it waits for a status change in progress and never shows the new clock with the old status.
     */
    @Transactional(readOnly = true)
    public Optional<RaceClockDto> clock(long raceId) {
        return raceRepository.findById(raceId)
                .map(race -> clockOf(race.getId(), race.getStatus(), durationMs(race)));
    }

    /**
     * The clock of a race the caller has already looked up, with the length its format gives it (null for
     * none). Shared by the boards, the overlay and the live feed so they all show the same time. The first time
     * a race is asked for since the app started, its stored times are read to work out its clock, so
     * {@code raceId} must be a real race.
     */
    public RaceClockDto clockOf(long raceId, RaceStatus status, Long durationMs) {
        Instant now = clock.instant();
        long elapsed;
        boolean running;
        if (status == RaceStatus.PENDING) {
            // A restart puts a race back to pending without a status event, so a kept clock may be stale
            elapsed = 0;
            running = false;
        } else {
            RaceClock kept = clockFor(raceId, now);
            elapsed = kept.elapsedMs(now);
            running = kept.running();
        }
        Long remaining = durationMs == null ? null : Math.max(0, durationMs - elapsed);
        return new RaceClockDto(raceId, status.name(), elapsed, durationMs, remaining, running);
    }

    /**
     * A race's length and, for a stagger start, the gap between cars.
     *
     * @param durationMs        each car's time on the track, or null for a race with no length
     * @param staggerIntervalMs the time between one car's start and the next in grid order, 0 unless the race starts
     *                          staggered
     * @param lapTarget         the laps each car runs when the race ends on a lap count, else null
     */
    public record RacePlan(Long durationMs, long staggerIntervalMs, Integer lapTarget) {
        /** For a race with no length and no stagger, or one that cannot be found. */
        public static final RacePlan NONE = new RacePlan(null, 0);

        /** A plan with no lap target: the race ends on its clock, or by hand. */
        public RacePlan(Long durationMs, long staggerIntervalMs) {
            this(durationMs, staggerIntervalMs, null);
        }
    }

    /**
     * The race's plan from its format, or empty for an unknown race. On the write connection, like {@link #clock}.
     * {@code LapTimingService} reads it once per race on the timing thread and keeps it, so a passing never queries.
     */
    @Transactional(readOnly = true)
    public Optional<RacePlan> planOf(long raceId) {
        return raceRepository.findById(raceId).map(race -> {
            EventClass eventClass = race.getEventClassId() == null ? null
                    : eventClassRepository.findById(race.getEventClassId()).orElse(null);
            StartType startType = startTypeOf(race);
            long intervalMs = startType == StartType.STAGGER
                    ? raceFormatService.staggerIntervalSeconds(eventClass) * 1000L : 0;
            return new RacePlan(durationMs(race), intervalMs, raceFormatService.lapTarget(eventClass).orElse(null));
        });
    }

    /**
     * When a race started and whether it starts rolling, for the total times in live timing. Empty for an unknown
     * race or one not started. On the write connection, like {@link #planOf}.
     *
     * @param startedAtMs epoch milliseconds the race started
     * @param rolling     whether the clock waits for the first car, so times count from its first crossing
     */
    public record RaceStart(long startedAtMs, boolean rolling) {}

    /**
     * The race's start, read by {@code LapTimingService} until the race's state has it. Rolling is the race's own
     * start type, the rule the stored result uses, so live and final total times agree.
     */
    @Transactional(readOnly = true)
    public Optional<RaceStart> startOf(long raceId) {
        return raceRepository.findById(raceId)
                .filter(race -> race.getStartedAt() != null)
                .map(race -> new RaceStart(race.getStartedAt().toEpochMilli(), race.getStartType() == StartType.ROLLING));
    }

    private Long durationMs(Race race) {
        if (race.getEventClassId() == null) {
            return null;
        }
        return roundRepository.findById(race.getRoundId())
                .flatMap(round -> eventClassRepository.findById(race.getEventClassId())
                        .map(eventClass -> raceFormatService.raceDurationMs(eventClass, round.getType())))
                .orElse(null);
    }

    /** Race time so far, not counting time stopped. */
    private static final class RaceClock {
        private long accumulatedMs;
        private Instant runningSince;
        /** Set once the clock has counted, or was worked out from stored times, so a resume never waits again. */
        private boolean hasRun;
        /** A rolling start that has been started and is waiting for its first car; the clock reads zero. */
        private boolean held;
        /** A car crossed before a rolling start's hold was set, so the hold is not needed. */
        private boolean passingSeen;

        /** A stopped clock holding the time from the race's start to its finish, or to now if it hasn't finished. */
        static RaceClock fromTimes(Race race, Instant now) {
            RaceClock raceClock = new RaceClock();
            if (race.getStartedAt() != null && race.getStatus() != RaceStatus.GRID) {
                Instant until = race.getStatus() == RaceStatus.FINISHED && race.getFinishedAt() != null
                        ? race.getFinishedAt() : now;
                raceClock.accumulatedMs = Math.max(0, Duration.between(race.getStartedAt(), until).toMillis());
                raceClock.hasRun = true;
            }
            return raceClock;
        }

        synchronized void start(Instant now) {
            held = false;
            hasRun = true;
            if (runningSince == null) {
                runningSince = now;
            }
        }

        /**
         * Starts the clock, or for a {@code rolling} start that has never run holds it for the first car, unless a
         * car has already crossed. A resume (the clock has run before) never holds.
         */
        synchronized void startOrHold(Instant now, boolean rolling) {
            if (rolling && !hasRun && !passingSeen) {
                held = true;
            } else {
                start(now);
            }
        }

        /** A car crossed: starts a held clock, or makes a hold that is about to be set unnecessary. */
        synchronized void onPassing(Instant now) {
            if (held) {
                start(now);
            } else if (!hasRun) {
                passingSeen = true;
            }
        }

        synchronized void stop(Instant now) {
            // A stopped race takes no first passing: the clock waits again when it is resumed
            held = false;
            passingSeen = false;
            if (runningSince != null) {
                accumulatedMs += Duration.between(runningSince, now).toMillis();
                runningSince = null;
            }
        }

        synchronized long elapsedMs(Instant now) {
            return accumulatedMs + (runningSince == null ? 0 : Duration.between(runningSince, now).toMillis());
        }

        synchronized boolean running() {
            return runningSince != null;
        }
    }
}
