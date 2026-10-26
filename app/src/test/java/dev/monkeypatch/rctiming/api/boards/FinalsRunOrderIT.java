package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.query.racecontrol.RunOrderQuery;
import dev.monkeypatch.rctiming.service.FinalsRunOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reordering the finals of an event where the A final runs in legs. */
class FinalsRunOrderIT extends AbstractIntegrationTest {

    @Autowired FinalsRunOrderService service;
    @Autowired RunOrderQuery runOrderQuery;
    @Autowired EventRepository eventRepository;
    @Autowired RacingClassRepository racingClassRepository;
    @Autowired EventClassRepository eventClassRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired RaceRepository raceRepository;
    @Autowired EntryRepository entryRepository;
    @Autowired RaceEntryRepository raceEntryRepository;
    @Autowired CompetitorService competitorService;

    private BoardFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new BoardFixtures(eventRepository, racingClassRepository, eventClassRepository,
                roundRepository, raceRepository, entryRepository, raceEntryRepository, competitorService);
    }

    @AfterEach
    void tearDown() {
        fx.cleanUp();
    }

    private Race finalRace(Round round, String letter, int leg) {
        Race race = fx.race(round, leg, RaceStatus.PENDING);
        race.setFinalLetter(letter);
        return fx.save(race);
    }

    private List<String> order() {
        return runOrderQuery.findForEvent(fx.event.getId()).stream()
                .map(i -> i.finalLetter() + i.heatNumber()).toList();
    }

    @Test
    void aLegOfTheAFinalCanRunBetweenOtherFinalsButNeverBeforeAnEarlierLeg() {
        Race b = finalRace(fx.round(RoundType.FINAL, 2, 1), "B", 1);
        Round aRound = fx.round(RoundType.FINAL, 1, 2);
        Race leg1 = finalRace(aRound, "A", 1);
        Race leg2 = finalRace(aRound, "A", 2);
        assertThat(order()).containsExactly("B1", "A1", "A2");

        service.move(Actor.system("test"), leg1.getId(), true);
        assertThat(order()).containsExactly("A1", "B1", "A2");

        service.move(Actor.system("test"), leg2.getId(), true);
        assertThat(order()).containsExactly("A1", "A2", "B1");

        assertThatThrownBy(() -> service.move(Actor.system("test"), leg2.getId(), true))
                .isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> service.move(Actor.system("test"), b.getId(), false))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    void finalsCannotBeReorderedWhenNoFinalRunsInLegs() {
        Race b = finalRace(fx.round(RoundType.FINAL, 2, 1), "B", 1);
        finalRace(fx.round(RoundType.FINAL, 1, 2), "A", 1);

        assertThatThrownBy(() -> service.move(Actor.system("test"), b.getId(), false))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("legs");
        assertThat(order()).containsExactly("B1", "A1");
    }

    @Test
    void aFinalThatHasBeenCalledCannotMove() {
        Race b = finalRace(fx.round(RoundType.FINAL, 2, 1), "B", 1);
        Round aRound = fx.round(RoundType.FINAL, 1, 2);
        finalRace(aRound, "A", 1);
        finalRace(aRound, "A", 2);
        b.setStatus(RaceStatus.GRID);
        fx.save(b);

        assertThatThrownBy(() -> service.move(Actor.system("test"), b.getId(), false))
                .isInstanceOf(StateConflictException.class);
    }
}
