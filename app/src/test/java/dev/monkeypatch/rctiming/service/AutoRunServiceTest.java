package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStateMachineService;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderItemDto;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoRunServiceTest {

    private static final long EVENT_ID = 7;

    private final MutableClock clock = new MutableClock();
    private final List<Race> races = new ArrayList<>();
    private final List<RunOrderItemDto> items = new ArrayList<>();
    private final RunOrderQuery runOrderQuery = mock(RunOrderQuery.class);
    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RoundRepository roundRepository = mock(RoundRepository.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final RaceStateMachineService stateMachine = mock(RaceStateMachineService.class);
    private final ClubProfileService clubProfileService = mock(ClubProfileService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final AuditService audit = mock(AuditService.class, Mockito.RETURNS_DEEP_STUBS);
    private final Event event = new Event();
    private AutoRunService service;

    @BeforeEach
    void setUp() {
        event.setId(EVENT_ID);
        event.setName("Club day");
        event.setAutoRunGapSeconds(180);
        event.setAutoRunHoldBeforeFinals(true);
        when(eventRepository.getOrThrow(EVENT_ID)).thenReturn(event);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(runOrderQuery.findForEvent(EVENT_ID)).thenAnswer(i -> List.copyOf(items));
        when(raceRepository.findById(anyLong())).thenAnswer(i -> races.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(raceRepository.getOrThrow(anyLong())).thenAnswer(i -> races.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst().orElseThrow());
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(anyLong())).thenReturn(List.of(new RaceEntry()));
        when(clubProfileService.audioSettings()).thenReturn(ClubAudioSettings.defaults());
        org.mockito.Mockito.doAnswer(i -> {
            ((Race) i.getArgument(0)).setStatus(RaceStatus.GRID);
            return null;
        }).when(stateMachine).callGrid(any());
        org.mockito.Mockito.doAnswer(i -> {
            ((Race) i.getArgument(0)).setStatus(RaceStatus.RUNNING);
            return null;
        }).when(stateMachine).start(any());
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new AutoRunService(runOrderQuery, raceRepository, roundRepository, raceEntryRepository,
                eventRepository, stateMachine, clubProfileService, messaging, new TransactionTemplate(tm), audit,
                clock);
    }

    private Race addRace(long id, RoundType type, RaceStatus status) {
        Race race = new Race();
        race.setId(id);
        race.setStatus(status);
        races.add(race);
        items.add(new RunOrderItemDto(id, 1, (int) id, 1, type.name(), "Buggy", 1,
                type == RoundType.FINAL ? "A" : null, status.name(), 1, null));
        return race;
    }

    /** Keeps the run order list in step with a race's status, as the database would. */
    private void sync(Race race) {
        for (int i = 0; i < items.size(); i++) {
            RunOrderItemDto it = items.get(i);
            if (it.raceId() == race.getId()) {
                items.set(i, new RunOrderItemDto(it.raceId(), it.eventClassId(), it.sequenceInEvent(),
                        it.roundNumber(), it.roundType(), it.className(), it.heatNumber(), it.finalLetter(),
                        race.getStatus().name(), it.sequenceInRound(), null));
            }
        }
    }

    private void finish(Race race, RoundType type) {
        race.setStatus(RaceStatus.FINISHED);
        sync(race);
        Round round = new Round();
        round.setEventId(EVENT_ID);
        round.setType(type);
        when(roundRepository.findById(race.getRoundId())).thenReturn(Optional.of(round));
        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, race.getId(), RaceStatus.FINISHED));
    }

    @Test
    void go_schedulesTheNextRaceAfterTheGap() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);

        var state = service.go(Actor.system("test"), EVENT_ID);

        assertThat(state.armed()).isTrue();
        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.WAITING);
        assertThat(state.nextRaceId()).isEqualTo(1L);
        assertThat(state.startAt()).isEqualTo(clock.instant().plusSeconds(180));
    }

    @Test
    void boardViewCountsDownWhileWaitingAndIsEmptyWhenOff() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        assertThat(service.boardView(EVENT_ID)).isEmpty();

        service.go(Actor.system("test"), EVENT_ID);

        var view = service.boardView(EVENT_ID).orElseThrow();
        assertThat(view.phase()).isEqualTo(AutoRunService.Phase.WAITING);
        assertThat(view.startAt()).isEqualTo(clock.instant().plusSeconds(180));
        assertThat(view.serverTime()).isEqualTo(clock.instant());
    }

    @Test
    void boardViewSaysWhyItIsHoldingBeforeTheFinals() {
        Race qualifier = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        addRace(2, RoundType.FINAL, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        finish(qualifier, RoundType.QUALIFIER);
        service.tick();

        var view = service.boardView(EVENT_ID).orElseThrow();
        assertThat(view.phase()).isEqualTo(AutoRunService.Phase.HELD);
        assertThat(view.heldReason()).isEqualTo(AutoRunService.HeldReason.FINALS);
    }

    @Test
    void callsTheGridAt30SecondsAndStartsAtZero() {
        Race race = addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);

        clock.advance(Duration.ofSeconds(149));
        service.tick();
        verify(stateMachine, never()).callGrid(any());

        clock.advance(Duration.ofSeconds(1));
        service.tick();
        verify(stateMachine).callGrid(race);
        verify(stateMachine, never()).start(any());

        clock.advance(Duration.ofSeconds(30));
        service.tick();
        verify(stateMachine).start(race);
        assertThat(service.state(EVENT_ID).phase()).isEqualTo(AutoRunService.Phase.RACING);
    }

    @Test
    void announcesTheTimeToTheNextRace() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);

        clock.advance(Duration.ofSeconds(60));
        service.tick();

        // 180 s gap: the 2 minute warning is due, the 5 and 10 minute ones do not fit
        verify(messaging).convertAndSend(eq("/topic/race/1/audio"),
                (Object) argThatText("Qualifying 1 — Buggy — Heat 1 in 2 minutes."));
        verify(messaging, never()).convertAndSend(eq("/topic/race/1/audio"),
                (Object) argThatText("Qualifying 1 — Buggy — Heat 1 in 5 minutes."));
    }

    @Test
    void sendsTheStartLeadInOnce() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);

        clock.advance(Duration.ofSeconds(170));
        service.tick();
        service.tick();

        verify(messaging, Mockito.times(1)).convertAndSend(eq("/topic/race/1/audio"),
                (Object) eq(Map.<String, Object>of("type", "start-countdown", "seconds", 10L)));
    }

    @Test
    void holdsAfterTheLastQualifierWhenTheNextRaceIsAFinal() {
        Race qualifier = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        addRace(2, RoundType.FINAL, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        assertThat(service.state(EVENT_ID).phase()).isEqualTo(AutoRunService.Phase.RACING);

        finish(qualifier, RoundType.QUALIFIER);
        service.tick();

        var state = service.state(EVENT_ID);
        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.HELD);
        assertThat(state.heldReason()).isEqualTo(AutoRunService.HeldReason.FINALS);
        assertThat(state.nextRaceId()).isEqualTo(2L);
        clock.advance(Duration.ofMinutes(10));
        service.tick();
        verify(stateMachine, never()).callGrid(any());
    }

    @Test
    void goReleasesTheFinalsHold() {
        Race qualifier = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        addRace(2, RoundType.FINAL, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        finish(qualifier, RoundType.QUALIFIER);
        service.tick();

        var state = service.go(Actor.system("test"), EVENT_ID);

        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.WAITING);
        assertThat(state.nextRaceId()).isEqualTo(2L);
    }

    @Test
    void carriesOnIntoTheFinalsWhenTheEventDoesNotHold() {
        event.setAutoRunHoldBeforeFinals(false);
        Race qualifier = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        addRace(2, RoundType.FINAL, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        finish(qualifier, RoundType.QUALIFIER);
        service.tick();

        assertThat(service.state(EVENT_ID).phase()).isEqualTo(AutoRunService.Phase.WAITING);
    }

    @Test
    void doesNotHoldBetweenFinals() {
        Race a = addRace(1, RoundType.FINAL, RaceStatus.RUNNING);
        addRace(2, RoundType.FINAL, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        finish(a, RoundType.FINAL);
        service.tick();

        assertThat(service.state(EVENT_ID).phase()).isEqualTo(AutoRunService.Phase.WAITING);
    }

    @Test
    void holdsWhenTheNextRaceHasNoDrivers() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(1L)).thenReturn(List.of());

        var state = service.go(Actor.system("test"), EVENT_ID);

        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.HELD);
        assertThat(state.heldReason()).isEqualTo(AutoRunService.HeldReason.NO_DRIVERS);
    }

    @Test
    void switchesItselfOffWhenTheRunOrderIsDone() {
        Race last = addRace(1, RoundType.FINAL, RaceStatus.RUNNING);
        service.go(Actor.system("test"), EVENT_ID);
        finish(last, RoundType.FINAL);
        service.tick();

        assertThat(service.state(EVENT_ID).armed()).isFalse();
    }

    @Test
    void skipToBringsTheStartForward() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);

        var state = service.skipTo(Actor.system("test"), EVENT_ID, 30);

        assertThat(state.startAt()).isEqualTo(clock.instant().plusSeconds(30));
    }

    @Test
    void stopSwitchesAutoRunOff() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);

        assertThat(service.stop(Actor.system("test"), EVENT_ID).armed()).isFalse();
        clock.advance(Duration.ofMinutes(10));
        service.tick();
        verify(stateMachine, never()).start(any());
    }

    @Test
    void holdsWhenTheServerRefusesACommand() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        org.mockito.Mockito.doThrow(new dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException("decoder offline")).when(stateMachine).callGrid(any());
        service.go(Actor.system("test"), EVENT_ID);

        clock.advance(Duration.ofSeconds(150));
        service.tick();

        var state = service.state(EVENT_ID);
        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.HELD);
        assertThat(state.heldReason()).isEqualTo(AutoRunService.HeldReason.REFUSED);
        assertThat(state.heldMessage()).contains("decoder offline");
    }

    @Test
    void anotherRaceFinishingDoesNotRestartTheCountdown() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        Race other = addRace(3, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        Instant startAt = service.state(EVENT_ID).startAt();

        clock.advance(Duration.ofSeconds(40));
        finish(other, RoundType.QUALIFIER);
        service.tick();

        assertThat(service.state(EVENT_ID).startAt()).isEqualTo(startAt);
    }

    @Test
    void holdsWhenTheGridCallIsTakenBack() {
        Race race = addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        clock.advance(Duration.ofSeconds(150));
        service.tick();
        race.setStatus(RaceStatus.PENDING);
        clock.advance(Duration.ofSeconds(30));
        service.tick();

        assertThat(service.state(EVENT_ID).phase()).isEqualTo(AutoRunService.Phase.HELD);
        verify(stateMachine, never()).start(any());
    }

    @Test
    void aRaceStartedByHandSwitchesAutoRunOnWhenTheEventAsksFor() {
        event.setAutoRunAutoStart(true);
        Race running = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        addRace(2, RoundType.QUALIFIER, RaceStatus.PENDING);
        Round round = new Round();
        round.setEventId(EVENT_ID);
        round.setType(RoundType.QUALIFIER);
        when(roundRepository.findById(running.getRoundId())).thenReturn(Optional.of(round));

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1, RaceStatus.RUNNING));
        service.tick();
        assertThat(service.state(EVENT_ID).armed()).isTrue();
        assertThat(service.state(EVENT_ID).autoStart()).isTrue();

        finish(running, RoundType.QUALIFIER);
        service.tick();
        var state = service.state(EVENT_ID);
        assertThat(state.phase()).isEqualTo(AutoRunService.Phase.WAITING);
        assertThat(state.nextRaceId()).isEqualTo(2L);
    }

    @Test
    void aRaceStartedByHandDoesNotSwitchItOnByDefault() {
        Race running = addRace(1, RoundType.QUALIFIER, RaceStatus.RUNNING);
        Round round = new Round();
        round.setEventId(EVENT_ID);
        when(roundRepository.findById(running.getRoundId())).thenReturn(Optional.of(round));

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1, RaceStatus.RUNNING));

        assertThat(service.state(EVENT_ID).armed()).isFalse();
    }

    @Test
    void aStoppedAutoRunStaysOffWhenARaceIsStartedByHand() {
        addRace(1, RoundType.QUALIFIER, RaceStatus.PENDING);
        service.go(Actor.system("test"), EVENT_ID);
        service.stop(Actor.system("test"), EVENT_ID);
        Race running = races.get(0);
        running.setStatus(RaceStatus.RUNNING);
        Round round = new Round();
        round.setEventId(EVENT_ID);
        when(roundRepository.findById(running.getRoundId())).thenReturn(Optional.of(round));

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, 1, RaceStatus.RUNNING));

        assertThat(service.state(EVENT_ID).armed()).isFalse();
    }

    private static Map<String, Object> argThatText(String text) {
        return org.mockito.ArgumentMatchers.argThat(m -> m instanceof Map<?, ?> map && text.equals(map.get("text")));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
