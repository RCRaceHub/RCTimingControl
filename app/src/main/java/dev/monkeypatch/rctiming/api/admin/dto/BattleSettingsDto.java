package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The club's on-track battle setting (#505), the body and answer of {@code GET} and {@code PUT
 * /api/v1/admin/club/battle-settings}. {@code gapMs} is the largest gap between adjacent cars that makes a battle, in
 * milliseconds; 0 switches battles off.
 */
public record BattleSettingsDto(@Min(0) @Max(10_000) int gapMs) {
}
