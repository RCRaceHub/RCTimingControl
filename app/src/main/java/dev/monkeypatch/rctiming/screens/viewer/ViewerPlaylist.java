package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** A playlist as a screen returns it from {@code GET} or {@code PUT /api/v1/playlist}. Unknown fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerPlaylist(List<Item> items, Long revision, @JsonProperty("updated_at") Double updatedAt,
                             String source) {

    /** One page: an absolute http(s) URL, its dwell in whole seconds, and a label and id echoed back in status. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String url, Integer duration, String label, String id) {
    }

    /** The body of {@code PUT /api/v1/playlist}; the whole playlist is replaced. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(List<Item> items, @JsonProperty("default_duration") Integer defaultDuration,
                       @JsonProperty("start_index") Integer startIndex, String source) {
    }
}
