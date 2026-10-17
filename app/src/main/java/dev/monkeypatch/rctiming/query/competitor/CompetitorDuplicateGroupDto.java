package dev.monkeypatch.rctiming.query.competitor;

import java.util.List;

/**
 * Competitors that may be the same person, and why they look alike (#123).
 *
 * @param reason      a sentence for the screen: same name, same BRCA number, or both
 * @param competitors the competitors in the group; at least two
 */
public record CompetitorDuplicateGroupDto(String reason, List<CompetitorSummaryDto> competitors) {
}
