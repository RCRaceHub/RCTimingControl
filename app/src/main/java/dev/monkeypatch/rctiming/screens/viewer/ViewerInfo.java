package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The answer of a screen's {@code GET /api/v1/info}. Unknown fields are ignored, as the contract allows v1 to grow.
 *
 * @param features what the agent offers ({@code playlist}, {@code control}, {@code message}, {@code banner} and so on);
 *                 null from an agent too old to list them
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerInfo(String id, String name, String hostname, String version,
                         @JsonProperty("api_version") String apiVersion, String browser,
                         @JsonProperty("auth_required") boolean authRequired, List<String> features, Limits limits) {

    /** True when the agent lists {@code feature}; false for one that does not, or that is too old to say. */
    public boolean has(String feature) {
        return features != null && features.contains(feature);
    }

    /** The shortest dwell and the default dwell the screen accepts, in whole seconds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Limits(@JsonProperty("default_duration") Integer defaultDuration,
                         @JsonProperty("min_duration") Integer minDuration) {
    }
}
