package dev.monkeypatch.rctiming.persistence.convert;

import dev.monkeypatch.rctiming.config.JacksonConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a value into JSON text for a column and back. Subclass it per value type; the jOOQ
 * repositories use the subclass to map that column. The columns hold JSON text guarded by
 * {@code CHECK (json_valid(...))}, so reading and writing is done here in Java, not by the database.
 * A null maps to a null column in both directions, and a value that cannot be written or a stored
 * text that cannot be read raises an {@link IllegalArgumentException}.
 *
 * @param <T> the Java type held in the column
 */
public abstract class JsonTextConverter<T> {

    /**
     * Strict about unknown properties and writing dates and durations as numbers, as the plain Jackson 2 mapper this
     * replaced was, so the text already stored in the columns reads back and new text matches it.
     */
    private static final ObjectMapper MAPPER = mapper();

    private static ObjectMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder().findAndAddModules();
        JacksonConfig.keepJackson2Defaults(builder);
        return builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .build();
    }

    private final TypeReference<T> type;

    protected JsonTextConverter(TypeReference<T> type) {
        this.type = type;
    }

    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Can't write " + attribute.getClass().getSimpleName() + " as JSON", e);
        }
    }

    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Can't read stored JSON as " + type.getType(), e);
        }
    }
}
