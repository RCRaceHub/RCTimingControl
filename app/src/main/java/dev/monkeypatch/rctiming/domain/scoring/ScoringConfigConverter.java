package dev.monkeypatch.rctiming.domain.scoring;

import tools.jackson.core.type.TypeReference;
import dev.monkeypatch.rctiming.persistence.convert.JsonTextConverter;

/** Stores a scoring config as JSON, keeping its {@code type} discriminator. */
public class ScoringConfigConverter extends JsonTextConverter<ScoringConfig> {

    public ScoringConfigConverter() {
        super(new TypeReference<>() {});
    }
}
