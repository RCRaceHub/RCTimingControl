package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.persistence.convert.JsonTextConverter;
import tools.jackson.core.type.TypeReference;

/** Stores a scoring config as JSON, keeping its {@code type} discriminator. */
public class ScoringConfigConverter extends JsonTextConverter<ScoringConfig> {

    public ScoringConfigConverter() {
        super(new TypeReference<>() {});
    }
}
