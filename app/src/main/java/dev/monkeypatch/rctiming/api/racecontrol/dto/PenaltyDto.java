package dev.monkeypatch.rctiming.api.racecontrol.dto;

import dev.monkeypatch.rctiming.domain.race.Penalty;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A lap or time penalty as the referee gave it, the answer of {@code POST /referee/race/{raceId}/penalty}.
 * {@code value} is whole laps for a LAP penalty and seconds for a TIME penalty; {@code appliedBy} is the official's
 * user id.
 */
public record PenaltyDto(Long id, Long raceId, Long entryId, PenaltyType penaltyType, BigDecimal value, String reason,
                         Long appliedBy, Instant appliedAt) {

    public static PenaltyDto from(Penalty p) {
        return new PenaltyDto(p.getId(), p.getRaceId(), p.getEntryId(), p.getPenaltyType(), p.getValue(),
                p.getReason(), p.getAppliedBy(), p.getAppliedAt());
    }
}
