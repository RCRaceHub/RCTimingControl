package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import dev.monkeypatch.rctiming.resultsexport.ResultsPublishTarget;
import java.util.List;

/**
 * @param sendingEnabled  whether the RaceHub address and key are both set; without them, exports wait
 * @param resultsUrl      the address in use, or null
 * @param savedUrl        the address set here, or null when the settings file's is in use
 * @param missingSettings what is still needed before anything is sent
 * @param keySource       where the key comes from: {@code SAVED} (set here), {@code SETTINGS_FILE} or {@code NONE}
 * @param publishTarget   where results are published now: the club's choice, else the default
 * @param chosenTarget    the club's choice, or null when it has not made one
 * @param exports         the newest exports first
 */
public record ResultsExportsDto(boolean sendingEnabled, String resultsUrl, String savedUrl,
                                List<String> missingSettings, String keySource, ResultsPublishTarget publishTarget,
                                ResultsPublishTarget chosenTarget,
                                List<ResultsOutboxQuery.OutboxRow> exports) {}
