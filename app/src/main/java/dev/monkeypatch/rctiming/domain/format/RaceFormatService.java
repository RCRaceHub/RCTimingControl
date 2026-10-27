package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.scoring.RaceScoringConfig;
import dev.monkeypatch.rctiming.domain.scoring.ScoringKind;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystem;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Race format templates, and the format a class actually races to. A class keeps its own snapshot of the
 * template it was given, so editing the template afterwards changes nothing already set up (FORMAT-06), and
 * an optional override patch is merged over that snapshot at read time (FORMAT-07).
 *
 * <p>The merge is one level deep: an override replaces a top-level field of the config, so it cannot change
 * part of a nested object. A key that is not a field of the config's type is not ignored either: the merge
 * fails, and every read of that class's format throws until the override is corrected.
 */
@Service
@Transactional
public class RaceFormatService {

    static final int DEFAULT_STAGGER_INTERVAL_SECONDS = 1;

    private final RaceFormatTemplateRepository templateRepository;
    private final EventClassRepository eventClassRepository;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final ScoringSystemRepository scoringSystemRepository;

    public RaceFormatService(
            RaceFormatTemplateRepository templateRepository,
            EventClassRepository eventClassRepository,
            ObjectMapper objectMapper,
            AuditService audit,
            ScoringSystemRepository scoringSystemRepository) {
        this.templateRepository = templateRepository;
        this.eventClassRepository = eventClassRepository;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.scoringSystemRepository = scoringSystemRepository;
    }

    /**
     * Returns the effective config for an EventClass by merging configSnapshot
     * with configOverride. Override values win. If override is null or empty,
     * the snapshot is returned directly. It reads no table, so it joins a transaction if there is one and takes
     * none otherwise, which keeps public reads off the write connection.
     *
     * @throws IllegalStateException if the override does not fit the config's type
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public RaceFormatConfig getEffectiveConfig(EventClass eventClass) {
        Map<String, Object> override = eventClass.getConfigOverride();
        if (override == null || override.isEmpty()) {
            return eventClass.getConfigSnapshot();
        }

        try {
            // Serialize snapshot to map
            Map<String, Object> snapshotMap = objectMapper.convertValue(
                    eventClass.getConfigSnapshot(),
                    new TypeReference<Map<String, Object>>() {}
            );

            // Overlay override values (override wins)
            snapshotMap.putAll(override);

            // Deserialize merged map back to RaceFormatConfig
            return objectMapper.convertValue(snapshotMap, RaceFormatConfig.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to merge format config override", e);
        }
    }

    /**
     * The class's Shikabane format, if it races to one. A format whose override cannot be merged counts as not
     * Shikabane here; the error surfaces where the format is read for its own sake.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<ShikabaneConfig> shikabaneConfig(EventClass eventClass) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return Optional.empty();
        }
        try {
            return getEffectiveConfig(eventClass) instanceof ShikabaneConfig s
                    ? Optional.of(s) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The laps a heat runs for when the class's format ends heats by lap count, else empty. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<Integer> lapTarget(EventClass eventClass) {
        return shikabaneConfig(eventClass).map(ShikabaneConfig::heatLaps).filter(laps -> laps > 0);
    }

    /**
     * A race's length from its class's format, by round type: finals of a points-and-finals format have their own
     * length. Null when the class has no format or the format sets no length.
     */
    @Transactional(readOnly = true)
    public Long raceDurationMs(EventClass eventClass, RoundType roundType) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return null;
        }
        int minutes = switch (getEffectiveConfig(eventClass)) {
            case TimedRaceConfig timed -> timed.durationMinutes();
            case BumpUpConfig bumpUp -> bumpUp.heatDurationMinutes();
            case PointsFinalsConfig points -> roundType == RoundType.FINAL
                    ? points.finalDurationMinutes() : points.heatDurationMinutes();
            case ShikabaneConfig shikabane -> shikabane.heatLaps() > 0 ? 0 : shikabane.heatDurationMinutes();
        };
        return minutes > 0 ? minutes * 60_000L : null;
    }

    /** The class's format when it is a Points Finals one, else empty. */
    private Optional<PointsFinalsConfig> pointsFinals(EventClass eventClass) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return Optional.empty();
        }
        return Optional.of(getEffectiveConfig(eventClass))
                .filter(PointsFinalsConfig.class::isInstance).map(PointsFinalsConfig.class::cast);
    }

    /** The most legs an A final can have, so a mistyped setting cannot generate a pile of races. */
    private static final int MAX_FINAL_LEGS = 10;

    /** How many races the class's A final is run in: 1 unless its Points Finals format sets more, up to 10. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public int finalLegs(EventClass eventClass) {
        return pointsFinals(eventClass).map(PointsFinalsConfig::finalLegs).map(legs -> Math.min(MAX_FINAL_LEGS, Math.max(1, legs))).orElse(1);
    }

    /** How many of a driver's A final legs count, 0 for all of them. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public int legsCounted(EventClass eventClass) {
        return pointsFinals(eventClass).map(PointsFinalsConfig::legsCounted).map(n -> Math.max(0, n)).orElse(0);
    }

    /**
     * The longest a race of this class may run, in milliseconds, when its format sets a maximum race window; empty
     * for no limit (the format has none, or the class has no readable format).
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<Long> maxRaceWindowMs(EventClass eventClass) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return Optional.empty();
        }
        return pointsFinals(eventClass)
                .map(PointsFinalsConfig::maxRaceWindowSeconds)
                .filter(seconds -> seconds > 0)
                .map(seconds -> seconds * 1000L);
    }

    /**
     * How a race in this class starts, from its format by round type: heats and practice take the qualifying start
     * and finals the finals start. A timed or Shikabane format uses its finals start when set and its start
     * otherwise. With no format, or none set in it, heats are staggered and finals start on the grid, as rounds
     * were generated before the format was read (#142).
     */
    @Transactional(readOnly = true)
    public StartType startType(EventClass eventClass, RoundType roundType) {
        boolean isFinal = roundType == RoundType.FINAL;
        StartType fromFormat = eventClass == null || eventClass.getConfigSnapshot() == null ? null
                : switch (getEffectiveConfig(eventClass)) {
                    case TimedRaceConfig timed -> isFinal && timed.finalsStartType() != null
                            ? timed.finalsStartType() : timed.startType();
                    case BumpUpConfig bumpUp -> isFinal ? bumpUp.finalsStartType() : bumpUp.qualifyingStartType();
                    case PointsFinalsConfig points -> isFinal ? points.finalsStartType() : points.qualifyingStartType();
                    case ShikabaneConfig shikabane -> isFinal && shikabane.finalsStartType() != null
                            ? shikabane.finalsStartType() : shikabane.startType();
                };
        if (fromFormat != null) {
            return fromFormat;
        }
        return isFinal ? StartType.GRID : StartType.STAGGER;
    }

    /**
     * The seconds between driver calls in a stagger start, from the class's format. One second when the class has
     * no format or the format sets none (FORMAT-11).
     */
    @Transactional(readOnly = true)
    public int staggerIntervalSeconds(EventClass eventClass) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return DEFAULT_STAGGER_INTERVAL_SECONDS;
        }
        int seconds = switch (getEffectiveConfig(eventClass)) {
            case TimedRaceConfig timed -> timed.staggerIntervalSeconds();
            case BumpUpConfig bumpUp -> bumpUp.staggerIntervalSeconds();
            case PointsFinalsConfig points -> points.staggerIntervalSeconds();
            case ShikabaneConfig shikabane -> shikabane.staggerIntervalSeconds();
        };
        return seconds > 0 ? seconds : DEFAULT_STAGGER_INTERVAL_SECONDS;
    }

    /**
     * Creates a new EventClass with a deep copy of the template's config as the
     * configSnapshot. The template reference is stored for audit purposes (FORMAT-06).
     * Template edits do not affect the snapshot after assignment.
     */
    public EventClass assignTemplateToEventClass(RaceFormatTemplate template) {
        // Deep copy: serialize then deserialize to break the reference. A format set from a scoring system takes the
        // system's values as they are now, so the class is scored as the system says on the day it was added.
        RaceFormatConfig snapshot = objectMapper.convertValue(
                resolveScoring(template.getConfig(), false),
                RaceFormatConfig.class
        );

        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(snapshot);
        eventClass.setTemplateId(template.getId());
        return eventClass;
    }

    /**
     * A Points Finals config set from a race scoring system, with that system's values copied over the config's own
     * scoring options; any other config, or one with no system, comes back as it is. Called when a template is saved
     * (strict) and when it is given to an event class (not strict). It runs inside its caller's write transaction.
     *
     * @param strict whether a system that is missing or not a race system is refused; when not strict the config's
     *               own values are kept, so a system deleted since the template was saved cannot block adding a class
     * @throws IllegalArgumentException (400) in strict mode when the id is not a race scoring system
     */
    RaceFormatConfig resolveScoring(RaceFormatConfig config, boolean strict) {
        if (!(config instanceof PointsFinalsConfig p) || p.scoringSystemId() == null) {
            return config;
        }
        ScoringSystem system = scoringSystemRepository.findById(p.scoringSystemId()).orElse(null);
        if (system == null || system.getKind() != ScoringKind.RACE) {
            if (strict) {
                throw new IllegalArgumentException("Scoring system " + p.scoringSystemId() + " is not a race scoring system");
            }
            return config;
        }
        RaceScoringConfig r = (RaceScoringConfig) system.getConfig();
        return new PointsFinalsConfig(p.qualifyingHeats(), p.finalsCount(), p.finalDurationMinutes(),
                p.heatDurationMinutes(), p.qualifyingStartType(), p.finalsStartType(), p.qualifyingType(),
                p.racePaddingMinutes(), p.staggerIntervalSeconds(), r.bestHeatsCount(), r.splitTiedPlaces(),
                r.brcaQualifying(), r.maxRaceWindowSeconds(), r.carsPerFinal(), r.fillTopFirst(), r.finalLegs(),
                r.legsCounted(), p.scoringSystemId());
    }

    /** Whether any format template is set from this scoring system, so it cannot be deleted. */
    @Transactional(readOnly = true)
    public boolean isScoringSystemInUse(Long scoringSystemId) {
        return templateRepository.findAll().stream().anyMatch(t ->
                t.getConfig() instanceof PointsFinalsConfig p && scoringSystemId.equals(p.scoringSystemId()));
    }

    /** Every format template. */
    @Transactional(readOnly = true)
    public List<RaceFormatTemplate> findAll() {
        return templateRepository.findAll();
    }

    /** A format template; a 404 if there is none. */
    @Transactional(readOnly = true)
    public RaceFormatTemplate findById(Long id) {
        return templateRepository.getOrThrow(id);
    }

    /** Adds a template and records {@code FORMAT_CREATED}. */
    public RaceFormatTemplate create(Actor actor, String name, RaceFormatConfig config) {
        return create(actor, "FORMAT_CREATED", "Added the race format ", name, config);
    }

    private RaceFormatTemplate create(Actor actor, String action, String summary, String name,
                                      RaceFormatConfig config) {
        RaceFormatTemplate template = new RaceFormatTemplate();
        template.setName(name);
        template.setConfig(resolveScoring(config, true));
        RaceFormatTemplate saved = templateRepository.save(template);
        audit.entry(actor, action).entity("race_format", saved.getId())
                .summary(summary + saved.getName())
                .after(values(saved)).record();
        return saved;
    }

    /** Changes a template. Events that already use it keep their own snapshot (FORMAT-06). */
    public RaceFormatTemplate update(Actor actor, Long id, String name, RaceFormatConfig config) {
        RaceFormatTemplate template = findById(id);
        Map<String, Object> before = values(template);
        template.setName(name);
        template.setConfig(resolveScoring(config, true));
        RaceFormatTemplate saved = templateRepository.save(template);
        audit.entry(actor, "FORMAT_UPDATED").entity("race_format", id)
                .summary("Changed the race format " + saved.getName())
                .before(before).after(values(saved)).record();
        return saved;
    }

    /**
     * Removes a template and records {@code FORMAT_DELETED}. Event classes that were given it keep their snapshot, and
     * lose the template reference.
     */
    public void delete(Actor actor, Long id) {
        RaceFormatTemplate template = findById(id);
        Map<String, Object> before = values(template);
        templateRepository.deleteById(id);
        audit.entry(actor, "FORMAT_DELETED").entity("race_format", id)
                .summary("Removed the race format " + template.getName())
                .before(before).record();
    }

    private static Map<String, Object> values(RaceFormatTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("config", t.getConfig());
        return m;
    }

    /**
     * A template's config, for {@link RaceFormatCodec} to write out (FORMAT-14). A scoring system id is a row number in
     * this club's database, so it is left out of the file; the scoring options in it are already the system's values.
     */
    public RaceFormatConfig exportConfig(Long id) {
        RaceFormatConfig config = findById(id).getConfig();
        if (config instanceof PointsFinalsConfig p && p.scoringSystemId() != null) {
            return new PointsFinalsConfig(p.qualifyingHeats(), p.finalsCount(), p.finalDurationMinutes(),
                    p.heatDurationMinutes(), p.qualifyingStartType(), p.finalsStartType(), p.qualifyingType(),
                    p.racePaddingMinutes(), p.staggerIntervalSeconds(), p.bestHeatsCount(), p.splitTiedPlaces(),
                    p.brcaQualifying(), p.maxRaceWindowSeconds(), p.carsPerFinal(), p.fillTopFirst(), p.finalLegs(),
                    p.legsCounted());
        }
        return config;
    }

    /** Adds a template from an imported config and records {@code FORMAT_IMPORTED} (FORMAT-14). */
    public RaceFormatTemplate importConfig(Actor actor, String name, RaceFormatConfig config) {
        return create(actor, "FORMAT_IMPORTED", "Imported the race format ", name, config);
    }
}
