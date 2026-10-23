package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs an event's races one after another with no one pressing anything (#338): when a race finishes, the next
 * one waits the event's gap, is called to the grid 30 seconds before it starts, gets its lead-in count, and
 * starts. An official switches it on for an event with {@link #go} and off with {@link #stop}.
 *
 * <p>It holds, rather than carrying on, after the last qualifier when the event asks for that, so the finals can
 * be checked and seeded again before an official lets them run, and when the next race has no drivers or a
 * command is refused. It also stops by itself when the run order is finished.
 *
 * <p>The plan lives in memory only: a restart of the app switches auto run off. Races are started through
 * {@link RaceStateMachineService}, as the race control commands are, so every listener reacts as it does to a
 * start from the screen. The announcements go out on the next race's audio topic and are spoken by the browsers
 * running race control; the schedule does not depend on a browser being open.
 */
@Service
public class AutoRunService {

    /** The least time left after a changed gap moves a countdown, so the start is not instant. */
    static final int MIN_RETIMED_SECONDS = 5;

    private static final Logger log = LoggerFactory.getLogger(AutoRunService.class);

    /** The grid is called this long before the start. */
    static final int GRID_CALL_SECONDS = 30;
    /** Seconds before the start at which the time to the next race is announced, longest first. */
    static final int[] WARNING_SECONDS = {600, 300, 120, 60};
    /** A warning is only made when the gap is at least this much longer, so it does not talk over the last race. */
    private static final int WARNING_MARGIN_SECONDS = 10;

    /** Where an armed event's auto run is: counting down to the next race, waiting for a race under way, or holding. */
    public enum Phase { WAITING, RACING, HELD }

    /** Why auto run is holding. */
    public enum HeldReason { FINALS, NO_DRIVERS, REFUSED }

    /**
     * What race control shows.
     *
     * @param armed         whether auto run is on for the event
     * @param phase         waiting to start the next race, waiting for a race under way, or holding; null when off
     * @param nextRaceId    the race that starts next (or the one under way)
     * @param nextRaceLabel its name
     * @param startAt       when the next race starts, while waiting
     * @param heldReason    why it is holding
     * @param heldMessage   the detail, for a refused command
     * @param gapSeconds    the event's gap between races
     * @param holdBeforeFinals whether the event holds before the finals
     * @param autoStart     whether auto run switches itself on when a race is started by hand
     * @param serverTime    the app's clock when this was built, so a browser whose own clock is out can still count
     *                      down to {@code startAt} correctly
     */
    public record AutoRunState(long eventId, boolean armed, Phase phase, Long nextRaceId, String nextRaceLabel,
                               Instant startAt, HeldReason heldReason, String heldMessage,
                               int gapSeconds, boolean holdBeforeFinals, boolean autoStart, Instant serverTime) {
    }

    /**
     * What the spectator boards show of auto run: no race names beyond the next race's, no refusal detail. The
     * countdown is {@code startAt} less {@code serverTime}, so it does not depend on the viewer's clock.
     *
     * @param phase      waiting to start the next race, waiting for a race under way, or holding
     * @param heldReason why it is holding, so a board can say "waiting for the finals" instead of counting
     * @param startAt    when the next race starts, while {@code phase} is WAITING
     * @param serverTime the app's clock when this was built
     */
    public record BoardView(Phase phase, HeldReason heldReason, Instant startAt, Instant serverTime) {
    }

    /**
     * One armed event's schedule. Written by request threads (under the service's monitor) and by the scheduler's
     * tick, and read by the status-changed listener without the monitor, so the fields it touches are volatile.
     */
    private static final class Plan {
        volatile Phase phase = Phase.RACING;
        volatile Long raceId;
        volatile String label;
        volatile Instant startAt;
        int totalSeconds;
        boolean gridCalled;
        boolean leadInSent;
        /** Whether the countdown is the event's gap, so a changed gap moves it; a lead-in for a race on the grid is not. */
        boolean gapCountdown;
        final Set<Integer> announced = new HashSet<>();
        volatile HeldReason heldReason;
        volatile String heldMessage;
        /** Set when a race of the event finished; the next tick plans the one after it. */
        volatile boolean replan;
        /** The type of the race that finished, or null when auto run was just switched on. */
        volatile RoundType finishedType;
        /** Auto run was switched on or resumed by an official, so the hold before the finals does not apply. */
        volatile boolean release = true;
    }

    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();
    /** Events whose auto run an official stopped, so a race started by hand does not switch it on again. */
    private final Set<Long> stoppedEvents = ConcurrentHashMap.newKeySet();

    private final RunOrderQuery runOrderQuery;
    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EventRepository eventRepository;
    private final RaceStateMachineService stateMachine;
    private final ClubProfileService clubProfileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Clock clock;

    @Autowired
    public AutoRunService(RunOrderQuery runOrderQuery, RaceRepository raceRepository, RoundRepository roundRepository,
                          RaceEntryRepository raceEntryRepository, EventRepository eventRepository,
                          RaceStateMachineService stateMachine, ClubProfileService clubProfileService,
                          SimpMessagingTemplate messagingTemplate, TransactionTemplate transactions,
                          AuditService audit) {
        this(runOrderQuery, raceRepository, roundRepository, raceEntryRepository, eventRepository, stateMachine,
                clubProfileService, messagingTemplate, transactions, audit, Clock.systemUTC());
    }

    AutoRunService(RunOrderQuery runOrderQuery, RaceRepository raceRepository, RoundRepository roundRepository,
                   RaceEntryRepository raceEntryRepository, EventRepository eventRepository,
                   RaceStateMachineService stateMachine, ClubProfileService clubProfileService,
                   SimpMessagingTemplate messagingTemplate, TransactionTemplate transactions, AuditService audit,
                   Clock clock) {
        this.runOrderQuery = runOrderQuery;
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.eventRepository = eventRepository;
        this.stateMachine = stateMachine;
        this.clubProfileService = clubProfileService;
        this.messagingTemplate = messagingTemplate;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Switches auto run on for the event, or releases a hold, and plans the next race. Runs on a request thread
     * and is audited ({@code AUTO_RUN_STARTED} or {@code AUTO_RUN_RESUMED}).
     */
    public synchronized AutoRunState go(Actor actor, long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        stoppedEvents.remove(eventId);
        boolean wasArmed = plans.containsKey(eventId);
        Plan plan = plans.computeIfAbsent(eventId, id -> new Plan());
        plan.release = true;
        plan.finishedType = null;
        plan.replan = false;
        plan(event, plan);
        record(actor, wasArmed ? "AUTO_RUN_RESUMED" : "AUTO_RUN_STARTED", event,
                (wasArmed ? "Resumed" : "Started") + " auto run for " + event.getName());
        return publish(event);
    }

    /** Switches auto run off. A race already called to the grid or running is left as it is. */
    public synchronized AutoRunState stop(Actor actor, long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        stoppedEvents.add(eventId);
        if (plans.remove(eventId) != null) {
            record(actor, "AUTO_RUN_STOPPED", event, "Stopped auto run for " + event.getName());
        }
        return publish(event);
    }

    /**
     * Brings the next start forward to {@code seconds} from now, as the 30s and 10s buttons of RC-Timing do. It
     * never pushes a start back, and does nothing to the plan unless the event is counting down; warnings that
     * would now be too early are marked as given so they are not spoken.
     */
    public synchronized AutoRunState skipTo(Actor actor, long eventId, int seconds) {
        Event event = eventRepository.getOrThrow(eventId);
        Plan plan = plans.get(eventId);
        if (plan != null && plan.phase == Phase.WAITING) {
            Instant soonest = clock.instant().plusSeconds(Math.max(0, seconds));
            if (soonest.isBefore(plan.startAt)) {
                plan.startAt = soonest;
                for (int warning : WARNING_SECONDS) {
                    if (warning > seconds) {
                        plan.announced.add(warning);
                    }
                }
            }
        }
        record(actor, "AUTO_RUN_SKIPPED", event, "Brought the next race start forward to " + seconds
                + " seconds for " + event.getName());
        return publish(event);
    }

    /**
     * Applies changed settings and pushes the event's state to open race control pages. Runs on the request thread
     * of the settings save. A countdown already running keeps the start it counted from but takes the new gap: it
     * now ends the new gap after it began, and no sooner than {@link #MIN_RETIMED_SECONDS} from now, so shortening
     * the gap to less than has already passed starts the race shortly rather than at once. Warnings that would now
     * be too early are marked as given.
     */
    public synchronized AutoRunState refresh(long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        Plan plan = plans.get(eventId);
        if (plan != null && plan.phase == Phase.WAITING && plan.gapCountdown && plan.startAt != null
                && plan.totalSeconds != event.getAutoRunGapSeconds()) {
            Instant now = clock.instant();
            Instant began = plan.startAt.minusSeconds(plan.totalSeconds);
            Instant moved = began.plusSeconds(event.getAutoRunGapSeconds());
            Instant earliest = now.plusSeconds(MIN_RETIMED_SECONDS);
            plan.startAt = moved.isBefore(earliest) ? earliest : moved;
            plan.totalSeconds = event.getAutoRunGapSeconds();
            long left = secondsLeft(plan);
            for (int warning : WARNING_SECONDS) {
                if (warning > left) {
                    plan.announced.add(warning);
                }
            }
        }
        return publish(event);
    }

    /** The event's current auto run state, for race control to load; off when the event has no plan. */
    public AutoRunState state(long eventId) {
        return stateOf(eventRepository.getOrThrow(eventId), plans.get(eventId));
    }

    /**
     * What the boards show for the event, or empty when auto run is off. Called on request threads by the anonymous
     * now-and-next board; reads the volatile plan fields without the service's monitor.
     */
    public Optional<BoardView> boardView(long eventId) {
        Plan plan = plans.get(eventId);
        return plan == null ? Optional.empty()
                : Optional.of(new BoardView(plan.phase, plan.heldReason, plan.startAt, clock.instant()));
    }

    /**
     * A race of an armed event finished: the next tick plans what follows. A race started by hand switches auto
     * run on for its event when the event asks for that and an official has not stopped it, so the races carry
     * on without anyone pressing Go.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() == RaceStatus.RUNNING) {
            armWhenStartedByHand(event.getRaceId());
            return;
        }
        if (event.getNewStatus() != RaceStatus.FINISHED || plans.isEmpty()) {
            return;
        }
        Race race = raceRepository.findById(event.getRaceId()).orElse(null);
        Round round = race == null ? null : roundRepository.findById(race.getRoundId()).orElse(null);
        Plan plan = round == null ? null : plans.get(round.getEventId());
        // Not under the service's monitor: this runs in the finishing request's write transaction, and the
        // scheduler holds the monitor while it waits for the write connection. A race other than the one being
        // waited on does not disturb its countdown.
        if (plan != null && (plan.phase != Phase.WAITING || Objects.equals(event.getRaceId(), plan.raceId))) {
            plan.finishedType = round.getType();
            plan.release = false;
            plan.replan = true;
        }
    }

    /** Plans in memory only, as the listener runs in the start's write transaction. The next tick fills it in. */
    private void armWhenStartedByHand(long raceId) {
        Race race = raceRepository.findById(raceId).orElse(null);
        Round round = race == null ? null : roundRepository.findById(race.getRoundId()).orElse(null);
        if (round == null || plans.containsKey(round.getEventId()) || stoppedEvents.contains(round.getEventId())) {
            return;
        }
        Event event = eventRepository.findById(round.getEventId()).orElse(null);
        if (event != null && event.isAutoRunAutoStart()) {
            Plan plan = new Plan();
            plan.release = false;
            plan.replan = true;
            if (plans.putIfAbsent(event.getId(), plan) == null) {
                // A stop that landed between the check above and the put has found nothing to remove
                if (stoppedEvents.contains(event.getId())) {
                    plans.remove(event.getId(), plan);
                    return;
                }
                log.info("Auto run switched on for event {} by the start of race {}", event.getId(), raceId);
            }
        }
    }

    /**
     * Advances every armed event once a second, on the scheduler thread: plans after a finish, calls the grid,
     * sends the lead-in and warnings, and starts the race when its time comes. Holds the service's monitor, so
     * {@link #go} and {@link #stop} wait for a tick in progress. A failure in one event holds that event with the
     * reason shown on race control and does not stop the others.
     */
    @Scheduled(initialDelay = 5_000, fixedDelay = 1_000)
    public synchronized void tick() {
        for (Map.Entry<Long, Plan> entry : plans.entrySet()) {
            try {
                tick(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                log.error("Auto run for event {} failed; holding", entry.getKey(), e);
                // Only a refused transition has a message fit for the screen
                hold(entry.getValue(), HeldReason.REFUSED, e instanceof IllegalStateTransitionException
                        ? e.getMessage() : "a race command failed, see the log");
                try {
                    eventRepository.findById(entry.getKey()).ifPresent(this::publish);
                } catch (RuntimeException publishFailure) {
                    log.warn("Could not publish the auto run state of event {}", entry.getKey(), publishFailure);
                }
            }
        }
    }

    private void tick(long eventId, Plan plan) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            plans.remove(eventId);
            return;
        }
        if (plan.replan || (plan.phase == Phase.RACING && raceNoLongerUnderway(plan))) {
            Phase before = plan.phase;
            Long beforeRace = plan.raceId;
            plan(event, plan);
            if (plan.phase != before || !Objects.equals(beforeRace, plan.raceId) || !plans.containsKey(eventId)) {
                publish(event);
            }
            return;
        }
        if (plan.phase != Phase.WAITING) {
            return;
        }
        Race race = raceRepository.findById(plan.raceId).orElse(null);
        if (race == null || (race.getStatus() != RaceStatus.PENDING && race.getStatus() != RaceStatus.GRID)) {
            // Someone started, finished or restarted it by hand
            plan.replan = true;
            return;
        }
        if (plan.gridCalled && race.getStatus() == RaceStatus.PENDING) {
            // The grid call was taken back: leave it to an official
            hold(plan, HeldReason.REFUSED, "the grid call was taken back");
            publish(event);
            return;
        }
        long left = secondsLeft(plan);
        announceWarnings(plan, left);
        if (left <= GRID_CALL_SECONDS && !plan.gridCalled && race.getStatus() == RaceStatus.PENDING) {
            plan.gridCalled = true;
            command(event, race, "RACE_GRID_CALLED", "Called the grid for", stateMachine::callGrid);
            // Nothing in the plan changed, but race control should show the race on the grid
            publish(event);
        }
        int leadIn = leadInSeconds();
        if (leadIn > 0 && left <= leadIn && !plan.leadInSent) {
            plan.leadInSent = true;
            cue(plan.raceId, Map.of("type", "start-countdown", "seconds", Math.max(left, 0)));
        }
        if (left <= 0) {
            if (race.getStatus() == RaceStatus.PENDING) {
                command(event, race, "RACE_GRID_CALLED", "Called the grid for", stateMachine::callGrid);
            }
            Race fresh = raceRepository.getOrThrow(plan.raceId);
            command(event, fresh, "RACE_STARTED", "Started", stateMachine::start);
            plan.phase = Phase.RACING;
            plan.startAt = null;
            publish(event);
        }
    }

    /** A restart puts a race back to pending without a status event, so a race being waited on is checked. */
    private boolean raceNoLongerUnderway(Plan plan) {
        return plan.raceId == null || raceRepository.findById(plan.raceId)
                .map(r -> r.getStatus() == RaceStatus.PENDING || r.getStatus() == RaceStatus.FINISHED)
                .orElse(true);
    }

    /** Works out what happens next from the run order: wait for a race under way, hold, or schedule the next. */
    private void plan(Event event, Plan plan) {
        plan.replan = false;
        List<RunOrderItemDto> items = runOrderQuery.findForEvent(event.getId());
        Optional<RunOrderItemDto> underway = items.stream()
                .filter(i -> "RUNNING".equals(i.status()) || "STOPPED".equals(i.status())).findFirst();
        if (underway.isPresent()) {
            setRace(plan, underway.get());
            plan.phase = Phase.RACING;
            plan.startAt = null;
            return;
        }
        Optional<RunOrderItemDto> next = items.stream()
                .filter(i -> "GRID".equals(i.status()) || "PENDING".equals(i.status()))
                .filter(i -> !isUnusedFinal(items, i)).findFirst();
        if (next.isEmpty()) {
            plans.remove(event.getId());
            log.info("Auto run for event {} finished: nothing left to run", event.getId());
            return;
        }
        RunOrderItemDto item = next.get();
        if (plan.phase == Phase.WAITING && Objects.equals(plan.raceId, item.raceId()) && plan.startAt != null) {
            return;
        }
        setRace(plan, item);
        boolean finalsNext = "FINAL".equals(item.roundType());
        if (!plan.release && event.isAutoRunHoldBeforeFinals() && finalsNext
                && plan.finishedType != null && plan.finishedType != RoundType.FINAL) {
            hold(plan, HeldReason.FINALS, null);
            cue(item.raceId(), Map.of("type", "auto-run-held",
                    "text", "Qualifying is complete. The finals need calculating in race control."));
            return;
        }
        if (raceEntryRepository.findByRaceIdOrderByGridPosition(item.raceId()).isEmpty()) {
            hold(plan, HeldReason.NO_DRIVERS, null);
            return;
        }
        plan.release = false;
        plan.phase = Phase.WAITING;
        plan.heldReason = null;
        plan.heldMessage = null;
        // A race already on the grid still gets its lead-in
        plan.gapCountdown = !"GRID".equals(item.status());
        plan.totalSeconds = plan.gapCountdown ? event.getAutoRunGapSeconds() : leadInSeconds();
        plan.startAt = clock.instant().plusSeconds(plan.totalSeconds);
        plan.gridCalled = "GRID".equals(item.status());
        plan.leadInSent = false;
        plan.announced.clear();
    }

    /**
     * Whether {@code item} is a final the calculation left with nobody, because the field was too small for all the
     * finals (the lowest ones stay empty): a pending final with an empty grid while another final of its class has
     * drivers. Auto run steps over it, since there is nothing to run and no one to fix. A final with an empty grid
     * and no calculated sibling is not this: the finals have not been calculated yet, so auto run holds for them.
     */
    private boolean isUnusedFinal(List<RunOrderItemDto> items, RunOrderItemDto item) {
        if (!"FINAL".equals(item.roundType()) || !"PENDING".equals(item.status())
                || !raceEntryRepository.findByRaceIdOrderByGridPosition(item.raceId()).isEmpty()) {
            return false;
        }
        return items.stream().anyMatch(other -> other.eventClassId() == item.eventClassId()
                && "FINAL".equals(other.roundType()) && other.raceId() != item.raceId()
                && !raceEntryRepository.findByRaceIdOrderByGridPosition(other.raceId()).isEmpty());
    }

    private void setRace(Plan plan, RunOrderItemDto item) {
        plan.raceId = item.raceId();
        plan.label = RaceLabel.of(item.roundType(), item.roundNumber(), item.className(), item.heatNumber(),
                item.finalLetter());
    }

    private void hold(Plan plan, HeldReason reason, String message) {
        plan.phase = Phase.HELD;
        plan.startAt = null;
        plan.heldReason = reason;
        plan.heldMessage = message;
    }

    private void announceWarnings(Plan plan, long left) {
        for (int warning : WARNING_SECONDS) {
            if (left <= warning && plan.announced.add(warning)) {
                // Only while it is still about right, and only if the gap leaves room after the last race
                if (left > warning - 5 && warning <= plan.totalSeconds - WARNING_MARGIN_SECONDS) {
                    int minutes = warning / 60;
                    cue(plan.raceId, Map.of("type", "next-race-warning",
                            "text", plan.label + " in " + minutes + (minutes == 1 ? " minute." : " minutes.")));
                }
            }
        }
    }

    private long secondsLeft(Plan plan) {
        return (long) Math.ceil(Duration.between(clock.instant(), plan.startAt).toMillis() / 1000.0);
    }

    private int leadInSeconds() {
        Integer seconds = clubProfileService.audioSettings().startCountdownSeconds();
        return seconds != null ? seconds : ClubAudioSettings.DEFAULT_START_COUNTDOWN_SECONDS;
    }

    private void cue(Long raceId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/race/" + raceId + "/audio", payload);
    }

    private interface RaceCommand {
        void run(Race race);
    }

    /**
     * Runs a race command in its own transaction, recorded as done by auto run ({@code system:auto-run}). Called on
     * the scheduler thread; an invalid transition throws and the tick holds the event.
     */
    private void command(Event event, Race race, String action, String verb, RaceCommand command) {
        transactions.executeWithoutResult(status -> {
            Race current = raceRepository.getOrThrow(race.getId());
            RaceStatus before = current.getStatus();
            command.run(current);
            audit.entry(Actor.system("auto-run"), action)
                    .entity("race", current.getId()).race(current.getId()).event(event.getId())
                    .summary(verb + " race " + current.getId() + " (auto run)")
                    .before(before).after(current.getStatus())
                    .record();
        });
    }

    private void record(Actor actor, String action, Event event, String summary) {
        transactions.executeWithoutResult(status -> audit.entry(actor, action)
                .entity("event", event.getId()).event(event.getId()).summary(summary).record());
    }

    /** Sends the event's state to {@code /topic/event/{id}/auto-run} for open race control pages. */
    private AutoRunState publish(Event event) {
        AutoRunState state = stateOf(event, plans.get(event.getId()));
        messagingTemplate.convertAndSend("/topic/event/" + event.getId() + "/auto-run", state);
        return state;
    }

    private AutoRunState stateOf(Event event, Plan plan) {
        if (plan == null) {
            return new AutoRunState(event.getId(), false, null, null, null, null, null, null,
                    event.getAutoRunGapSeconds(), event.isAutoRunHoldBeforeFinals(), event.isAutoRunAutoStart(),
                    clock.instant());
        }
        return new AutoRunState(event.getId(), true, plan.phase, plan.raceId, plan.label, plan.startAt,
                plan.heldReason, plan.heldMessage, event.getAutoRunGapSeconds(), event.isAutoRunHoldBeforeFinals(),
                event.isAutoRunAutoStart(), clock.instant());
    }
}
