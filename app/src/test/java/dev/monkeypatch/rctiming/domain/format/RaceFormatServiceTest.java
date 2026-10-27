package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.scoring.ChampionshipScoringConfig;
import dev.monkeypatch.rctiming.domain.scoring.RaceScoringConfig;
import dev.monkeypatch.rctiming.domain.scoring.ScoringConfig;
import dev.monkeypatch.rctiming.domain.scoring.ScoringKind;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystem;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RaceFormatServiceTest {

    private RaceFormatService service;
    private ObjectMapper objectMapper;
    private ScoringSystemRepository scoringSystems;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        scoringSystems = mock(ScoringSystemRepository.class);
        service = new RaceFormatService(
                mock(RaceFormatTemplateRepository.class),
                mock(EventClassRepository.class),
                objectMapper,
                mock(dev.monkeypatch.rctiming.domain.audit.AuditService.class),
                scoringSystems
        );
    }

    @Test
    void getEffectiveConfig_withNullOverride_returnsSnapshotUnchanged() {
        TimedRaceConfig snapshot = new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3);
        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(snapshot);
        eventClass.setConfigOverride(null);

        RaceFormatConfig effective = service.getEffectiveConfig(eventClass);

        assertThat(effective).isEqualTo(snapshot);
    }

    @Test
    void getEffectiveConfig_withOverride_appliesOverridedField() throws Exception {
        TimedRaceConfig snapshot = new TimedRaceConfig(10, StartType.STAGGER, QualifyingType.FTQ, 2, 3);
        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(snapshot);
        eventClass.setConfigOverride(Map.of("durationMinutes", 15));

        RaceFormatConfig effective = service.getEffectiveConfig(eventClass);

        assertThat(effective).isInstanceOf(TimedRaceConfig.class);
        TimedRaceConfig result = (TimedRaceConfig) effective;
        assertThat(result.durationMinutes()).isEqualTo(15);
        assertThat(result.startType()).isEqualTo(StartType.STAGGER);
        assertThat(result.qualifyingType()).isEqualTo(QualifyingType.FTQ);
        assertThat(result.racePaddingMinutes()).isEqualTo(2);
    }

    @Test
    void getEffectiveConfig_withUnknownOverrideField_isIgnored() throws Exception {
        TimedRaceConfig snapshot = new TimedRaceConfig(5, StartType.GRID, QualifyingType.FTQ, 2, 0);
        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(snapshot);
        eventClass.setConfigOverride(Map.of("unknownFutureField", "someValue"));

        RaceFormatConfig effective = service.getEffectiveConfig(eventClass);

        assertThat(effective).isInstanceOf(TimedRaceConfig.class);
        TimedRaceConfig result = (TimedRaceConfig) effective;
        assertThat(result.durationMinutes()).isEqualTo(5);
    }

    @Test
    void assignTemplateToEventClass_createsDeepCopy_notReference() throws Exception {
        TimedRaceConfig originalConfig = new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 3);
        RaceFormatTemplate template = new RaceFormatTemplate();
        template.setId(42L);
        template.setName("5-minute timed");
        template.setConfig(originalConfig);

        EventClass eventClass = service.assignTemplateToEventClass(template);

        assertThat(eventClass.getConfigSnapshot()).isEqualTo(originalConfig);
        assertThat(eventClass.getConfigSnapshot()).isNotSameAs(originalConfig);
        assertThat(eventClass.getTemplateId()).isEqualTo(42L);
    }

    @Test
    void startType_comesFromTheFormatByRoundType() {
        EventClass timed = classWith(new TimedRaceConfig(5, StartType.ROLLING, QualifyingType.FTQ, 2, 0));
        assertThat(service.startType(timed, RoundType.QUALIFIER)).isEqualTo(StartType.ROLLING);
        assertThat(service.startType(timed, RoundType.FINAL)).isEqualTo(StartType.ROLLING);

        EventClass bumpUp = classWith(new BumpUpConfig(3, 5, 2, 10, 2, StartType.GRID, StartType.ROLLING,
                QualifyingType.FTQ, 2, 0));
        assertThat(service.startType(bumpUp, RoundType.PRACTICE)).isEqualTo(StartType.GRID);
        assertThat(service.startType(bumpUp, RoundType.FINAL)).isEqualTo(StartType.GRID);

        EventClass points = classWith(new PointsFinalsConfig(3, 2, 8, 5, StartType.ROLLING, StartType.STAGGER,
                QualifyingType.FTQ, 2, 0));
        assertThat(service.startType(points, RoundType.QUALIFIER)).isEqualTo(StartType.ROLLING);
        assertThat(service.startType(points, RoundType.FINAL)).isEqualTo(StartType.GRID);
    }

    @Test
    void startType_withNoneInTheFormat_isStaggeredForHeatsAndGridForFinals() {
        EventClass unset = classWith(new TimedRaceConfig(5, null, QualifyingType.FTQ, 2, 0));
        assertThat(service.startType(unset, RoundType.QUALIFIER)).isEqualTo(StartType.STAGGER);
        assertThat(service.startType(unset, RoundType.FINAL)).isEqualTo(StartType.GRID);

        EventClass noFormat = new EventClass();
        assertThat(service.startType(noFormat, RoundType.PRACTICE)).isEqualTo(StartType.STAGGER);
        assertThat(service.startType(noFormat, RoundType.FINAL)).isEqualTo(StartType.GRID);
    }

    @Test
    void startType_followsAnOverride() {
        EventClass eventClass = classWith(new TimedRaceConfig(5, StartType.STAGGER, QualifyingType.FTQ, 2, 0));
        eventClass.setConfigOverride(Map.of("startType", "ROLLING"));

        assertThat(service.startType(eventClass, RoundType.QUALIFIER)).isEqualTo(StartType.ROLLING);
    }

    private static EventClass classWith(RaceFormatConfig config) {
        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(config);
        return eventClass;
    }

    private PointsFinalsConfig pointsWithSystem(Long systemId) {
        return new PointsFinalsConfig(3, 2, 5, 5, StartType.STAGGER, StartType.GRID, QualifyingType.ROUND_BY_ROUND, 2, 5,
                0, false, false, 0, 0, false, 0, 0, systemId);
    }

    private void systemExists(Long id, ScoringKind kind,
                              ScoringConfig config) {
        var system = new ScoringSystem();
        system.setId(id);
        system.setKind(kind);
        system.setConfig(config);
        org.mockito.Mockito.when(scoringSystems.findById(id)).thenReturn(Optional.of(system));
    }

    @Test
    void resolveScoring_copiesTheSystemsValuesOverTheFormatsOwn() {
        systemExists(7L, ScoringKind.RACE,
                new RaceScoringConfig.Brca(2, true, 390, 3, 2, 10, true));

        PointsFinalsConfig resolved = (PointsFinalsConfig) service.resolveScoring(pointsWithSystem(7L), true);

        assertThat(resolved.bestHeatsCount()).isEqualTo(2);
        assertThat(resolved.splitTiedPlaces()).isTrue();
        assertThat(resolved.brcaQualifying()).isTrue();
        assertThat(resolved.maxRaceWindowSeconds()).isEqualTo(390);
        assertThat(resolved.finalLegs()).isEqualTo(3);
        assertThat(resolved.legsCounted()).isEqualTo(2);
        assertThat(resolved.carsPerFinal()).isEqualTo(10);
        assertThat(resolved.fillTopFirst()).isTrue();
        assertThat(resolved.scoringSystemId()).isEqualTo(7L);
        assertThat(resolved.finalsCount()).as("layout and timing settings are untouched").isEqualTo(2);
    }

    @Test
    void resolveScoring_aConfigWithNoSystemComesBackAsItIs() {
        PointsFinalsConfig byHand = pointsWithSystem(null);
        assertThat(service.resolveScoring(byHand, true)).isSameAs(byHand);
    }

    @Test
    void resolveScoring_strictRefusesAMissingOrChampionshipSystem_lenientKeepsTheFormat() {
        systemExists(8L, ScoringKind.CHAMPIONSHIP,
                new ChampionshipScoringConfig(null, null, 0, 0, false, null));

        assertThatThrownBy(() -> service.resolveScoring(pointsWithSystem(8L), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveScoring(pointsWithSystem(99L), true))
                .isInstanceOf(IllegalArgumentException.class);
        PointsFinalsConfig lenient = pointsWithSystem(99L);
        assertThat(service.resolveScoring(lenient, false)).isSameAs(lenient);
    }
}
