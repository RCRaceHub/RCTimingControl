package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The answer of a screen's {@code GET /api/v1/info}. Unknown fields are ignored, as the contract allows v1 to grow. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerInfo(String id, String name, String hostname, String version,
                         @JsonProperty("api_version") String apiVersion, String browser,
                         @JsonProperty("auth_required") boolean authRequired, Limits limits) {

    /** The shortest dwell and the default dwell the screen accepts, in whole seconds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Limits(@JsonProperty("default_duration") Integer defaultDuration,
                         @JsonProperty("min_duration") Integer minDuration) {
    }
}
