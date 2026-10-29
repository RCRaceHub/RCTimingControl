package dev.monkeypatch.rctiming.domain.format;

import dev.monkeypatch.rctiming.TestJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceFormatConfigSerdeTest {

    private ObjectMapper jsonMapper;
    private ObjectMapper yamlMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = TestJson.mapper();
        yamlMapper = TestJson.yamlMapper();
    }

    @Test
    void timedRaceConfig_roundTrips_throughJson() throws JacksonException {
        TimedRaceConfig original = new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3);

        String json = jsonMapper.writeValueAsString(original);
        RaceFormatConfig deserialized = jsonMapper.readValue(json, RaceFormatConfig.class);

        assertThat(deserialized).isInstanceOf(TimedRaceConfig.class);
        assertThat(json).contains("\"type\":\"TIMED\"");
        TimedRaceConfig result = (TimedRaceConfig) deserialized;
        assertThat(result.durationMinutes()).isEqualTo(5);
        assertThat(result.startType()).isEqualTo(StartType.STAGGER);
        assertThat(result.qualifyingType()).isEqualTo(QualifyingType.FTQ);
    }

    @Test
    void shikabaneConfig_roundTrips_throughJson() throws JacksonException {
        ShikabaneConfig original = new ShikabaneConfig(5, StartType.GRID, 3, 3, 1, 0, 0, 0, 0, false);

        String json = jsonMapper.writeValueAsString(original);
        RaceFormatConfig deserialized = jsonMapper.readValue(json, RaceFormatConfig.class);

        assertThat(json).contains("\"type\":\"SHIKABANE\"");
        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void bumpUpConfig_roundTrips_throughJson() throws JacksonException {
        BumpUpConfig original = new BumpUpConfig(
                3, 5, 2, 8, 2,
                StartType.GRID, StartType.GRID,
                QualifyingType.ROUND_BY_ROUND, 2, 5);

        String json = jsonMapper.writeValueAsString(original);
        RaceFormatConfig deserialized = jsonMapper.readValue(json, RaceFormatConfig.class);

        assertThat(deserialized).isInstanceOf(BumpUpConfig.class);
        assertThat(json).contains("\"type\":\"BUMP_UP\"");
        BumpUpConfig result = (BumpUpConfig) deserialized;
        assertThat(result.qualifyingHeats()).isEqualTo(3);
        assertThat(result.bumpSpots()).isEqualTo(2);
        assertThat(result.qualifyingType()).isEqualTo(QualifyingType.ROUND_BY_ROUND);
    }

    @Test
    void pointsFinalsConfig_roundTrips_throughJson() throws JacksonException {
        PointsFinalsConfig original = new PointsFinalsConfig(
                4, 3, 10, 5,
                StartType.ROLLING, StartType.GRID,
                QualifyingType.FASTEST_LAP, 2, 0);

        String json = jsonMapper.writeValueAsString(original);
        RaceFormatConfig deserialized = jsonMapper.readValue(json, RaceFormatConfig.class);

        assertThat(deserialized).isInstanceOf(PointsFinalsConfig.class);
        assertThat(json).contains("\"type\":\"POINTS_FINALS\"");
        PointsFinalsConfig result = (PointsFinalsConfig) deserialized;
        assertThat(result.finalsCount()).isEqualTo(3);
        assertThat(result.qualifyingType()).isEqualTo(QualifyingType.FASTEST_LAP);
    }

    @Test
    void pointsFinalsConfigSavedBeforeTheBrcaSettings_loadsWithTheOldBehaviour() throws JacksonException {
        String old = "{\"type\":\"POINTS_FINALS\",\"qualifyingHeats\":3,\"finalsCount\":2,"
                + "\"finalDurationMinutes\":5,\"heatDurationMinutes\":5,\"qualifyingStartType\":\"STAGGER\","
                + "\"finalsStartType\":\"GRID\",\"qualifyingType\":\"ROUND_BY_ROUND\","
                + "\"racePaddingMinutes\":2,\"staggerIntervalSeconds\":5}";

        PointsFinalsConfig result = (PointsFinalsConfig) jsonMapper.readValue(old, RaceFormatConfig.class);

        assertThat(result.bestHeatsCount()).isZero();
        assertThat(result.splitTiedPlaces()).isFalse();
        assertThat(result.brcaQualifying()).isFalse();
        assertThat(result.maxRaceWindowSeconds()).isZero();
        assertThat(result.carsPerFinal()).isZero();
        assertThat(result.fillTopFirst()).isFalse();
        assertThat(result.finalLegs()).isZero();
        assertThat(result.legsCounted()).isZero();
    }

    @Test
    void timedRaceConfig_roundTrips_throughYaml() throws JacksonException {
        TimedRaceConfig original = new TimedRaceConfig(8, StartType.GRID, QualifyingType.CONSECUTIVE_LAPS, 3, 0);

        String yaml = yamlMapper.writeValueAsString(original);
        RaceFormatConfig deserialized = yamlMapper.readValue(yaml, RaceFormatConfig.class);

        assertThat(deserialized).isInstanceOf(TimedRaceConfig.class);
        TimedRaceConfig result = (TimedRaceConfig) deserialized;
        assertThat(result.durationMinutes()).isEqualTo(8);
        assertThat(result.startType()).isEqualTo(StartType.GRID);
        assertThat(result.qualifyingType()).isEqualTo(QualifyingType.CONSECUTIVE_LAPS);
    }

    @Test
    void unknownTypeDiscriminator_throwsInvalidTypeIdException() {
        String json = "{\"type\":\"UNKNOWN_FORMAT\",\"durationMinutes\":5}";

        assertThatThrownBy(() -> jsonMapper.readValue(json, RaceFormatConfig.class))
                .isInstanceOf(InvalidTypeIdException.class);
    }

    @Test
    void extraFields_areIgnored_duringDeserialization() throws JacksonException {
        String json = "{\"type\":\"TIMED\",\"durationMinutes\":5,\"startType\":\"STAGGER\"," +
                "\"qualifyingType\":\"FTQ\",\"racePaddingMinutes\":2,\"staggerIntervalSeconds\":3," +
                "\"unknownFutureField\":\"someValue\"}";

        RaceFormatConfig deserialized = jsonMapper.readValue(json, RaceFormatConfig.class);

        assertThat(deserialized).isInstanceOf(TimedRaceConfig.class);
        assertThat(((TimedRaceConfig) deserialized).durationMinutes()).isEqualTo(5);
    }

    @Test
    void startTypeEnum_serializesAndDeserializes_correctly() throws JacksonException {
        for (StartType type : StartType.values()) {
            String json = jsonMapper.writeValueAsString(type);
            StartType deserialized = jsonMapper.readValue(json, StartType.class);
            assertThat(deserialized).isEqualTo(type);
        }
    }

    @Test
    void qualifyingTypeEnum_serializesAndDeserializes_correctly() throws JacksonException {
        for (QualifyingType type : QualifyingType.values()) {
            String json = jsonMapper.writeValueAsString(type);
            QualifyingType deserialized = jsonMapper.readValue(json, QualifyingType.class);
            assertThat(deserialized).isEqualTo(type);
        }
    }
}
