package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.IncidentReportRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsence;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsenceRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalPenalty;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyKind;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Penalty;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefereeServiceTest {

    @Mock IncidentReportRepository incidentReportRepository;
    @Mock PenaltyRepository penaltyRepository;
    @Mock MarshalAbsenceRepository marshalAbsenceRepository;
    @Mock MarshalPenaltyRepository marshalPenaltyRepository;
    @Mock RaceRepository raceRepository;
    @Mock LapTimingService lapTimingService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) AuditService audit;
    @Mock RaceAuditLabels labels;
    @Mock FinalsMarshalPenalties finalsMarshalPenalties;
    @Mock EntryRepository entryRepository;

    private RefereeService service() {
        return new RefereeService(incidentReportRepository, penaltyRepository, marshalAbsenceRepository,
                marshalPenaltyRepository, raceRepository, lapTimingService, eventPublisher, audit, labels,
                finalsMarshalPenalties, entryRepository);
    }

    @Test
    void aLapPenaltyInARunningRace_comesOffLiveTimingAndSendsNoCorrection() {
        when(penaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race(RaceStatus.RUNNING)));

        Penalty penalty = service().applyPenalty(7L, 11L, PenaltyType.LAP, BigDecimal.valueOf(2), "Cut", 3L);

        assertThat(penalty.getPenaltyType()).isEqualTo(PenaltyType.LAP);
        verify(lapTimingService).applyLapPenalty(7L, 11L, 2);
        verify(lapTimingService).dismissJumpStart(7L, 11L);
        verify(audit, never()).entry(any(), eq("JUMP_START_PENALISED"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aPenaltyForACarThatJumpedTheStart_isAuditedAsSettlingIt() {
        when(penaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race(RaceStatus.RUNNING)));
        when(lapTimingService.openJumpStart(7L, 11L))
                .thenReturn(Optional.of(new JumpStartDto(11L, "Sam Fox", "12", 2400, false)));

        service().applyPenalty(7L, 11L, PenaltyType.LAP, BigDecimal.valueOf(1), "Jump start", 3L);

        verify(audit).entry(any(), eq("JUMP_START_PENALISED"));
        verify(lapTimingService).dismissJumpStart(7L, 11L);
    }

    @Test
    void takingNoActionOnAJumpStart_isAuditedAndTellsLiveTiming() {
        when(lapTimingService.openJumpStart(7L, 11L))
                .thenReturn(Optional.of(new JumpStartDto(11L, "Sam Fox", "12", 2400, true)));

        service().dismissJumpStart(7L, 11L, 3L);

        verify(audit).entry(any(), eq("JUMP_START_DISMISSED"));
        verify(lapTimingService).dismissJumpStart(7L, 11L);
    }

    @Test
    void takingNoActionOnACarThatDidNotJump_isAConflict() {
        when(lapTimingService.openJumpStart(7L, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().dismissJumpStart(7L, 11L, 3L))
                .isInstanceOf(StateConflictException.class);
        verifyNoInteractions(audit);
        verify(lapTimingService, never()).dismissJumpStart(anyLong(), anyLong());
    }

    @Test
    void aTimePenaltyAfterTheFinish_sendsTheResultsAgainAndLeavesLiveTimingAlone() {
        when(penaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race(RaceStatus.FINISHED)));

        service().applyPenalty(7L, 11L, PenaltyType.TIME, new BigDecimal("5.5"), "Track limits", 3L);

        verify(eventPublisher).publishEvent(new FinishedRaceCorrected(7L));
        verify(lapTimingService, never()).applyLapPenalty(anyLong(), anyLong(), anyInt());
        verify(lapTimingService, never()).applyTimePenalty(anyLong(), anyLong(), anyLong());
    }

    @Test
    void aTimePenaltyInARunningRace_isAddedToLiveTimingInMilliseconds() {
        when(penaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race(RaceStatus.RUNNING)));

        service().applyPenalty(7L, 11L, PenaltyType.TIME, new BigDecimal("5.5"), "Track limits", 3L);

        verify(lapTimingService).applyTimePenalty(7L, 11L, 5500L);
        verify(lapTimingService, never()).applyLapPenalty(anyLong(), anyLong(), anyInt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aLapPenaltyThatIsNotAWholeNumberOrIsTooBig_isRefusedBeforeAnythingIsSaved() {
        assertThatThrownBy(() -> service().applyPenalty(7L, 11L, PenaltyType.LAP, new BigDecimal("1.5"), "x", 3L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().applyPenalty(7L, 11L, PenaltyType.LAP, BigDecimal.valueOf(101), "x", 3L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(penaltyRepository, lapTimingService, eventPublisher);
    }

    @Test
    void aMarshalPenalty_forAnAbsenceOfAnotherEntry_isAConflict() {
        entryInEvent(1L);
        when(marshalAbsenceRepository.getOrThrow(5L)).thenReturn(absence(5L, 99L, 1L));

        assertThatThrownBy(() -> service()
                .applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.FINALS_LAP, 5L, null, 3L))
                .isInstanceOf(StateConflictException.class);
        verifyNoInteractions(marshalPenaltyRepository);
    }

    @Test
    void aMarshalPenalty_namingNoAbsence_isForTheEntrysLatestInTheEvent() {
        entryInEvent(1L);
        when(marshalAbsenceRepository.findByEventId(1L))
                .thenReturn(List.of(absence(4L, 11L, 1L), absence(6L, 11L, 1L), absence(8L, 99L, 1L)));
        when(marshalPenaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MarshalPenalty penalty = service().applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.QUALIFYING_SCORE, null,
                "Missed turn 3", 3L);

        assertThat(penalty.getAbsenceId()).isEqualTo(6L);
        assertThat(penalty.getKind()).isEqualTo(MarshalPenaltyKind.QUALIFYING_SCORE);
        verify(eventPublisher, never()).publishEvent(any(FinishedRaceCorrected.class));
    }

    @Test
    void aFinalsLapMarshalPenalty_correctsTheDriversFirstFinalWhenItHasFinished() {
        entryInEvent(1L);
        recordingAbsencesSucceeds();
        when(marshalPenaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(finalsMarshalPenalties.firstFinalOf(11L, 4L)).thenReturn(Optional.of(race(RaceStatus.FINISHED)));

        MarshalPenalty penalty = service().applyMarshalPenalty(8L, 11L, 1L, MarshalPenaltyKind.FINALS_LAP, null,
                null, 3L);

        assertThat(penalty.getKind()).isEqualTo(MarshalPenaltyKind.FINALS_LAP);
        verify(eventPublisher).publishEvent(new FinishedRaceCorrected(7L));
    }

    @Test
    void aFinalsLapMarshalPenalty_leavesAFinalThatHasNotFinishedToBeCorrectedAtItsFinish() {
        entryInEvent(1L);
        recordingAbsencesSucceeds();
        when(marshalPenaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(finalsMarshalPenalties.firstFinalOf(11L, 4L)).thenReturn(Optional.of(race(RaceStatus.PENDING)));

        service().applyMarshalPenalty(8L, 11L, 1L, MarshalPenaltyKind.FINALS_LAP, null, null, 3L);

        verify(eventPublisher, never()).publishEvent(any(FinishedRaceCorrected.class));
    }

    @Test
    void aMarshalPenalty_withNoAbsenceRecorded_recordsTheMissItself() {
        entryInEvent(1L);
        recordingAbsencesSucceeds();
        when(marshalPenaltyRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        MarshalPenalty penalty = service().applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.QUALIFYING_SCORE, null,
                null, 3L);

        verify(marshalAbsenceRepository).save(any(MarshalAbsence.class));
        assertThat(penalty.getAbsenceId()).isEqualTo(21L);
    }

    @Test
    void aMarshalPenalty_whenTheDriverIsPenalisedForThisRaceAlready_isAConflict() {
        entryInEvent(1L);
        MarshalAbsence missed = absence(6L, 11L, 1L);
        missed.setRaceId(7L);
        when(marshalAbsenceRepository.findByEventId(1L)).thenReturn(List.of(missed));
        when(marshalPenaltyRepository.existsByAbsenceId(6L)).thenReturn(true);

        assertThatThrownBy(() -> service()
                .applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.FINALS_LAP, null, null, 3L))
                .isInstanceOf(StateConflictException.class);
        verify(marshalAbsenceRepository, never()).save(any());
    }

    @Test
    void aMarshalPenalty_forAnEntryOfAnotherEvent_isAConflict() {
        entryInEvent(2L);

        assertThatThrownBy(() -> service()
                .applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.QUALIFYING_SCORE, null, null, 3L))
                .isInstanceOf(StateConflictException.class);
        verifyNoInteractions(marshalPenaltyRepository);
    }

    @Test
    void aSecondMarshalPenalty_forTheSameAbsence_isAConflict() {
        entryInEvent(1L);
        when(marshalAbsenceRepository.getOrThrow(5L)).thenReturn(absence(5L, 11L, 1L));
        when(marshalPenaltyRepository.existsByAbsenceId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service()
                .applyMarshalPenalty(7L, 11L, 1L, MarshalPenaltyKind.FINALS_LAP, 5L, null, 3L))
                .isInstanceOf(StateConflictException.class);
        verify(marshalPenaltyRepository, never()).save(any());
    }

    private void recordingAbsencesSucceeds() {
        when(marshalAbsenceRepository.save(any())).thenAnswer(call -> {
            MarshalAbsence saved = call.getArgument(0);
            saved.setId(21L);
            return saved;
        });
    }

    private Entry entryInEvent(long eventId) {
        Entry entry = new Entry();
        entry.setEventId(eventId);
        entry.setEventClassId(4L);
        when(entryRepository.getOrThrow(11L)).thenReturn(entry);
        return entry;
    }

    private static Race race(RaceStatus status) {
        Race race = new Race();
        race.setId(7L);
        race.setStatus(status);
        return race;
    }

    private static MarshalAbsence absence(long id, long entryId, long eventId) {
        MarshalAbsence absence = new MarshalAbsence();
        absence.setId(id);
        absence.setEntryId(entryId);
        absence.setEventId(eventId);
        return absence;
    }
}
