package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The settings of a race format, one of three kinds chosen by the JSON {@code type} discriminator: {@code TIMED}
 * ({@link TimedRaceConfig}), {@code BUMP_UP} ({@link BumpUpConfig}) or {@code POINTS_FINALS} ({@link
 * PointsFinalsConfig}). The hierarchy is sealed so code that handles a config can switch over it exhaustively, and a
 * config is validated by whether Jackson can read it as one of them. Stored as JSON text on templates and on event
 * classes (see {@link RaceFormatConfigConverter}), and exported and imported by {@link RaceFormatCodec} (FORMAT-14).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TimedRaceConfig.class,    name = "TIMED"),
    @JsonSubTypes.Type(value = BumpUpConfig.class,       name = "BUMP_UP"),
    @JsonSubTypes.Type(value = PointsFinalsConfig.class, name = "POINTS_FINALS")
})
public sealed interface RaceFormatConfig
    permits TimedRaceConfig, BumpUpConfig, PointsFinalsConfig {}
