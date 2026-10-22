package dev.monkeypatch.rctiming.domain.scoring;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The rules of a scoring system, chosen by the JSON {@code type} discriminator: {@code CLASSIC} and {@code BRCA}
 * ({@link RaceScoringConfig}) for races, {@code PLACE_POINTS} ({@link ChampionshipScoringConfig}) for championships.
 * A new kind of rule is a new subtype: one record, its {@link #validate()} and an editor form. The hierarchy is sealed
 * so code that handles a config can switch over it exhaustively, and a file cannot carry logic, only numbers and
 * switches. Stored as JSON text by {@link ScoringConfigConverter} and exported and imported by {@link ScoringCodec}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RaceScoringConfig.Classic.class, name = "CLASSIC"),
    @JsonSubTypes.Type(value = RaceScoringConfig.Brca.class, name = "BRCA"),
    @JsonSubTypes.Type(value = ChampionshipScoringConfig.class, name = "PLACE_POINTS")
})
public sealed interface ScoringConfig permits RaceScoringConfig, ChampionshipScoringConfig {

    /** The kind of system this config belongs to. */
    ScoringKind kind();

    /**
     * Checks the numbers are in range.
     *
     * @throws IllegalArgumentException (HTTP 400) naming the first value that is not allowed
     */
    void validate();
}
