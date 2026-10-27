package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriver;
import dev.monkeypatch.rctiming.domain.practice.PracticeDriverRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSession;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeStatus;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionServiceTest {

    private final PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
    private final PracticeDriverRepository drivers = mock(PracticeDriverRepository.class);
    private final RaceRepository races = mock(RaceRepository.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private final PracticeSessionService service = new PracticeSessionService(sessions,
            mock(EventRepository.class), mock(UserRepository.class), mock(PracticeTimingService.class), audit, races,
            drivers);

    private static PracticeSession session(long id) {
        PracticeSession s = mock(PracticeSession.class);
        when(s.getId()).thenReturn(id);
        return s;
    }

    private static PracticeDriver driver(long sessionId, String transponder, long competitorId) {
        PracticeDriver d = new PracticeDriver();
        d.setPracticeSessionId(sessionId);
        d.setTransponderNumber(transponder);
        d.setCompetitorId(competitorId);
        return d;
    }

    @Test
    void start_keepsTheNewestNameGivenToEachTransponderInEarlierSessions() {
        PracticeSession first = session(1);
        PracticeSession second = session(2);
        PracticeSession current = session(3);
        when(current.getStatus()).thenReturn(PracticeStatus.IDLE);
        when(sessions.getOrThrow(3L)).thenReturn(current);
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(races.findFirstByStatus(any())).thenReturn(Optional.empty());
        when(sessions.findStartedBetween(any(), any())).thenReturn(List.of(first, second, current));
        when(drivers.findByPracticeSessionId(1L)).thenReturn(List.of(driver(1, "111", 10), driver(1, "222", 20)));
        when(drivers.findByPracticeSessionId(2L)).thenReturn(List.of(driver(2, "111", 11)));

        service.start(Actor.system("test"), 3L);

        ArgumentCaptor<PracticeDriver> saved = ArgumentCaptor.forClass(PracticeDriver.class);
        verify(drivers, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(PracticeDriver::getPracticeSessionId).containsOnly(3L);
        assertThat(saved.getAllValues()).extracting(PracticeDriver::getTransponderNumber, PracticeDriver::getCompetitorId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("111", 11L), org.assertj.core.groups.Tuple.tuple("222", 20L));
    }
}
