package dev.monkeypatch.rctiming.timing;

import org.springframework.context.ApplicationEventPublisher;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** A passing that arrives after its race was released is dropped until the race runs again (#250). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LapTimingServiceReleasedTest {

    private static final long RACE_ID = 7L;

    @Mock LiveTimingHub hub;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock EntryRepository entryRepository;
    @Mock CompetitorRepository competitorRepository;
    @Mock LapThresholdQuery lapThresholdQuery;

    private LapTimingService service;

    @BeforeEach
    void setUp() {
        service = new LapTimingService(hub, raceEntryRepository, entryRepository, competitorRepository,
                lapThresholdQuery, mock(RaceClockService.class), mock(ApplicationEventPublisher.class));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(anyLong())).thenReturn(List.of());
    }

    private void passing() {
        service.onLapPassing(new LapPassingEvent(RACE_ID, "123456", 1_000_000L));
    }

    @Test
    void aLatePassingDoesNotRecreateTheStateOfAReleasedRace() {
        passing();
        service.releaseState(RACE_ID);

        passing();

        assertThat(service.peek(RACE_ID)).isEmpty();
    }

    @Test
    void theRaceTakesPassingsAgainOnceItIsRunning() {
        service.releaseState(RACE_ID);

        service.onRaceStatusChanged(new RaceStatusChangedEvent(this, RACE_ID, RaceStatus.RUNNING));
        passing();

        assertThat(service.peek(RACE_ID)).isPresent();
    }

    @Test
    void aRaceThatWasNeverReleasedTakesPassings() {
        passing();

        assertThat(service.peek(RACE_ID)).isPresent();
    }
}
