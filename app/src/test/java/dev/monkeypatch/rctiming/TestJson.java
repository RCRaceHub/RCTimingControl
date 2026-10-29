package dev.monkeypatch.rctiming;

import dev.monkeypatch.rctiming.config.JacksonConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Mappers for tests that build their own, set up like the one the running app uses (see {@link JacksonConfig}). */
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
        YAMLMapper.Builder builder = YAMLMapper.builder();
        JacksonConfig.keepJackson2Defaults(builder);
        return builder.build();
    }
}
