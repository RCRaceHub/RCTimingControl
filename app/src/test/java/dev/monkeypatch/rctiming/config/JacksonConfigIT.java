package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The mapper Spring Boot builds (REST, STOMP, live feed, results export) keeps Jackson 2 behaviour. */
class JacksonConfigIT extends AbstractIntegrationTest {

    @Autowired
    private JsonMapper mapper;

    record Probe(String zebra, String apple, boolean flag, Colour colour) {
    }

    enum Colour {
        RED {
            @Override
            public String toString() {
                return "a warm red";
            }
        }
    }

    @Test
    void writesPropertiesInTheirDeclaredOrderAndEnumsByName() {
        assertThat(mapper.writeValueAsString(new Probe("z", "a", true, Colour.RED)))
                .isEqualTo("{\"zebra\":\"z\",\"apple\":\"a\",\"flag\":true,\"colour\":\"RED\"}");
    }

    @Test
    void readsNullForAPrimitiveAsFalseAndIgnoresTextAfterTheDocumentAndUnknownProperties() {
        String json = "{\"zebra\":\"z\",\"flag\":null,\"colour\":\"RED\",\"extra\":1} trailing";

        Probe probe = mapper.readValue(json, Probe.class);

        assertThat(probe).isEqualTo(new Probe("z", null, false, Colour.RED));
    }

    @Test
    void hasTheFeaturesSet() {
        assertThat(mapper.isEnabled(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)).isFalse();
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isFalse();
        assertThat(mapper.isEnabled(EnumFeature.WRITE_ENUMS_USING_TO_STRING)).isFalse();
        assertThat(mapper.isEnabled(EnumFeature.READ_ENUMS_USING_TO_STRING)).isFalse();
    }
}
