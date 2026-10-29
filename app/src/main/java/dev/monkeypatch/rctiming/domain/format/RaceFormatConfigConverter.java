package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.persistence.convert.JsonTextConverter;
import tools.jackson.core.type.TypeReference;

/** Stores a race format config as JSON, keeping its {@code type} discriminator. */
public class RaceFormatConfigConverter extends JsonTextConverter<RaceFormatConfig> {

    public RaceFormatConfigConverter() {
        super(new TypeReference<>() {});
    }
}
