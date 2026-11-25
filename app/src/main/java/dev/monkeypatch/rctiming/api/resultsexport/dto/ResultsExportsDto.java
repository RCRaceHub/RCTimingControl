package dev.monkeypatch.rctiming.api.resultsexport.dto;

import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import dev.monkeypatch.rctiming.domain.club.ResultsPublishTarget;
import java.util.List;

/**
 * @param publishTarget   where results are published now: the club's choice, else the default
 * @param chosenTarget    the club's choice, or null when it has not made one
 * @param exports         the newest exports first
 */
public record ResultsExportsDto(ResultsPublishTarget publishTarget,
                                ResultsPublishTarget chosenTarget,
                                List<ResultsOutboxQuery.OutboxRow> exports) {}
