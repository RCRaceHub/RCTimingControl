package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.timing.JumpStartDetectedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JumpStartAuditTest {

    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private final RaceAuditLabels labels = mock(RaceAuditLabels.class);
    private final JumpStartAudit listener = new JumpStartAudit(audit, labels);

    @Test
    void aDetectedJumpStartIsWrittenToTheAuditLog() {
        listener.onJumpStartDetected(new JumpStartDetectedEvent(7L, 11L, 2400));

        verify(audit).entry(any(), eq("JUMP_START_DETECTED"));
    }

    @Test
    void aDatabaseFaultDoesNotReachTheTimingThread() {
        when(labels.eventOf(7L)).thenThrow(new IllegalStateException("database is locked"));

        assertThatCode(() -> listener.onJumpStartDetected(new JumpStartDetectedEvent(7L, 11L, 2400)))
                .doesNotThrowAnyException();
    }
}
