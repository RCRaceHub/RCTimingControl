package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The answer of a screen's {@code GET /api/v1/status}, which every control endpoint returns too. Times are the
 * screen's own clock in Unix seconds. Unknown fields are ignored.
 *
 * @param state    {@code idle}, {@code playing}, {@code paused} or {@code override}
 * @param time     the screen's clock, for spotting a wrong one
 * @param override the temporary page while one is showing, otherwise null
 * @param network  null before the screen's first reachability check and on its idle page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerStatus(String id, Double uptime, Double time, String state, boolean paused, Integer index,
                           Current current, ShowOverride override, Network network, PlaylistSummary playlist,
                           Browser browser) {

    /** The page on screen; {@code endsAt} is null when it will not change by itself. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(String url, String label, String id, @JsonProperty("started_at") Double startedAt,
                          @JsonProperty("ends_at") Double endsAt) {
    }

    /** A temporary page; {@code until} is null when it stays until cleared. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShowOverride(String url, Double until) {
    }

    /** Whether the screen can reach the page's server, checked every ten seconds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Network(@JsonProperty("page_reachable") Boolean pageReachable,
                          @JsonProperty("checked_at") Double checkedAt) {
    }

    /** What playlist the screen holds: its revision, length and the source tag the manager set. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistSummary(Long revision, Integer length, @JsonProperty("updated_at") Double updatedAt,
                                  String source) {
    }

    /** The browser the agent drives; a climbing {@code restarts} means it keeps crashing. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Browser(String driver, boolean running, Integer pid, String url, Integer restarts,
                          @JsonProperty("last_error") String lastError) {
    }
}
