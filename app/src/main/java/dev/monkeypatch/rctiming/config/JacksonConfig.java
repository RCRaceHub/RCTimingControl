package dev.monkeypatch.rctiming.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.MapperBuilder;

/**
 * Keeps the JSON the app reads and writes as it was under Jackson 2. Jackson 3 changed some defaults, and the REST
 * API, the STOMP messages, the exported files and the JSON columns in the database are contracts that must not change
 * with the library: property order is the declared order, not alphabetical; {@code null} for a primitive reads as zero
 * or {@code false}; text after the document is ignored; and enums go by their name, not their {@code toString()}.
 *
 * <p>Spring Boot's {@code JsonMapper} bean gets these through {@link #jackson2Defaults()}. The mapper in
 * {@code JsonTextConverter}, which Boot does not configure, calls {@link #keepJackson2Defaults} itself.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /** Applies {@link #keepJackson2Defaults} to the mapper Spring Boot builds, after its own settings. */
    @Bean
    JsonMapperBuilderCustomizer jackson2Defaults() {
        return JacksonConfig::keepJackson2Defaults;
    }

    /** Sets the Jackson 3 defaults that differ from Jackson 2 back to what Jackson 2 did. */
    public static <B extends MapperBuilder<?, B>> void keepJackson2Defaults(B builder) {
        builder.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING);
    }
}
