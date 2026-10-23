package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.timing.JumpStartDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a {@code JUMP_START_DETECTED} audit row when the timing code sees a car cross the line before its turn in a
 * stagger start, so the log shows the jump start whatever the referee later decides. The decisions are
 * {@code JUMP_START_DISMISSED} and {@code PENALTY_APPLIED} (see {@link RefereeService}).
 */
@Component
public class JumpStartAudit {

    private static final Logger log = LoggerFactory.getLogger(JumpStartAudit.class);

    private final AuditService audit;
    private final RaceAuditLabels labels;

    public JumpStartAudit(AuditService audit, RaceAuditLabels labels) {
        this.audit = audit;
        this.labels = labels;
    }

    /**
     * Runs on the timing thread, in a transaction of its own (a jump start is rare, so the write is cheap). A database
     * fault is logged and swallowed: it must not lose the passing that is being timed.
     */
    @EventListener
    public void onJumpStartDetected(JumpStartDetectedEvent event) {
        try {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("entryId", event.entryId());
            after.put("earlyMs", event.earlyMs());
            audit.entry(Actor.system("timing"), "JUMP_START_DETECTED").entity("race_entry", event.entryId())
                    .race(event.raceId()).event(labels.eventOf(event.raceId()))
                    .summary("Jump start by " + labels.driver(event.entryId()) + " in " + labels.race(event.raceId())
                            + " (left " + String.format(Locale.ROOT, "%.1f", event.earlyMs() / 1000.0) + " s early)")
                    .after(after).recordStandalone();
        } catch (RuntimeException e) {
            log.warn("Could not audit the jump start by entry {} in race {}", event.entryId(), event.raceId(), e);
        }
    }
}
