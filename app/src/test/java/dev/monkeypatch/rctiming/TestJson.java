package dev.monkeypatch.rctiming;

import dev.monkeypatch.rctiming.config.JacksonConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Mappers for tests that build their own, with the same Jackson settings as the app (see {@link JacksonConfig}). */
public final class TestJson {

    private TestJson() {
    }

    /** A JSON mapper with the app's Jackson settings. */
    public static ObjectMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        JacksonConfig.keepJackson2Defaults(builder);
        return builder.build();
    }

    /** A YAML mapper with the app's Jackson settings. */
    public static ObjectMapper yamlMapper() {
        return JacksonConfig.yamlMapper();
    }
}
