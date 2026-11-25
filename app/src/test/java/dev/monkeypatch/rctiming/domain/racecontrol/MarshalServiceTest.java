package dev.monkeypatch.rctiming.domain.racecontrol;

import dev.monkeypatch.rctiming.domain.race.MarshalAdjustment;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustmentRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import dev.monkeypatch.rctiming.domain.result.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.MarshalAdjustmentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarshalServiceTest {

    @Mock RaceRepository raceRepository;
    @Mock MarshalAdjustmentRepository marshalAdjustmentRepository;
    @Mock UserRepository userRepository;
    @Mock LapTimingService lapTimingService;
    @Mock ApplicationEventPublisher eventPublisher;

    private MarshalService service() {
        return new MarshalService(raceRepository, marshalAdjustmentRepository, userRepository, lapTimingService,
                eventPublisher);
    }

    @Test
    void adjust_whileRunning_recordsItAndAppliesItToLiveTiming() {
        when(raceRepository.getOrThrow(7L)).thenReturn(race(7L, RaceStatus.RUNNING));
        when(userRepository.findById(3L)).thenReturn(Optional.of(official("Sam", "Marshal", "sam@club.test")));

        MarshalAdjustment adjustment = service().adjust(7L, 11L, "1234567", -1, 3L);

        verify(marshalAdjustmentRepository).save(adjustment);
        assertThat(adjustment.getRaceStateAtTime()).isEqualTo("RUNNING");
        assertThat(adjustment.getActingUserName()).isEqualTo("Sam Marshal");
        assertThat(adjustment.getAdjustedAt()).isNotNull();
        ArgumentCaptor<MarshalAdjustmentDto> dto = ArgumentCaptor.forClass(MarshalAdjustmentDto.class);
        verify(lapTimingService).applyMarshalAdjustment(eq(7L), eq(11L), eq(-1), dto.capture());
        assertThat(dto.getValue().actingUserName()).isEqualTo("Sam Marshal");
        assertThat(dto.getValue().adjustedAtEpochMs()).isEqualTo(adjustment.getAdjustedAt().toEpochMilli());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void adjust_afterTheFinish_sendsTheResultsAgainInsteadOfTouchingLiveTiming() {
        when(raceRepository.getOrThrow(7L)).thenReturn(race(7L, RaceStatus.FINISHED));
        when(userRepository.findById(3L)).thenReturn(Optional.of(official(null, null, "ref@club.test")));

        MarshalAdjustment adjustment = service().adjust(7L, 11L, "1234567", 1, 3L);

        assertThat(adjustment.getActingUserName()).isEqualTo("ref@club.test");
        // The corrected results are built from the stored adjustments, so the row must be saved first
        InOrder inOrder = inOrder(marshalAdjustmentRepository, eventPublisher);
        inOrder.verify(marshalAdjustmentRepository).save(adjustment);
        inOrder.verify(eventPublisher).publishEvent(new FinishedRaceCorrected(7L));
        verify(lapTimingService, never()).applyMarshalAdjustment(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void adjust_byMoreThanOneLap_isRefusedAndRecordsNothing() {
        assertThatThrownBy(() -> service().adjust(7L, 11L, "1234567", 2, 3L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(raceRepository, marshalAdjustmentRepository, lapTimingService, eventPublisher);
    }

    private static Race race(long id, RaceStatus status) {
        Race race = new Race();
        race.setId(id);
        race.setStatus(status);
        return race;
    }

    private static User official(String firstName, String lastName, String email) {
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        return user;
    }
}
