package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;

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

    public RaceFormatService(
            RaceFormatTemplateRepository templateRepository,
            EventClassRepository eventClassRepository,
            ObjectMapper objectMapper,
            AuditService audit) {
        this.templateRepository = templateRepository;
        this.eventClassRepository = eventClassRepository;
        this.objectMapper = objectMapper;
        this.audit = audit;
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

    /**
     * The longest a race of this class may run, in milliseconds, when its format sets a maximum race window; empty
     * for no limit (the format has none, or the class has no readable format).
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<Long> maxRaceWindowMs(EventClass eventClass) {
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return Optional.empty();
        }
        return Optional.of(getEffectiveConfig(eventClass))
                .filter(PointsFinalsConfig.class::isInstance).map(PointsFinalsConfig.class::cast)
                .map(PointsFinalsConfig::maxRaceWindowSeconds)
                .filter(seconds -> seconds > 0)
                .map(seconds -> seconds * 1000L);
    }

    /**
     * How a race in this class starts, from its format by round type: heats and practice take the qualifying start
     * and finals the finals start, while a timed format has one start for both. With no format, or none set in it,
     * heats are staggered and finals start on the grid, as rounds were generated before the format was read (#142).
     */
    @Transactional(readOnly = true)
    public StartType startType(EventClass eventClass, RoundType roundType) {
        boolean isFinal = roundType == RoundType.FINAL;
        StartType fromFormat = eventClass == null || eventClass.getConfigSnapshot() == null ? null
                : switch (getEffectiveConfig(eventClass)) {
                    case TimedRaceConfig timed -> timed.startType();
                    case BumpUpConfig bumpUp -> isFinal ? bumpUp.finalsStartType() : bumpUp.qualifyingStartType();
                    case PointsFinalsConfig points -> isFinal ? points.finalsStartType() : points.qualifyingStartType();
                    case ShikabaneConfig shikabane -> shikabane.startType();
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
        // Deep copy: serialize then deserialize to break the reference
        RaceFormatConfig snapshot = objectMapper.convertValue(
                template.getConfig(),
                RaceFormatConfig.class
        );

        EventClass eventClass = new EventClass();
        eventClass.setConfigSnapshot(snapshot);
        eventClass.setTemplateId(template.getId());
        return eventClass;
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
        template.setConfig(config);
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
        template.setConfig(config);
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

    /** A template's config, for {@link RaceFormatCodec} to write out (FORMAT-14). */
    public RaceFormatConfig exportConfig(Long id) {
        return findById(id).getConfig();
    }

    /** Adds a template from an imported config and records {@code FORMAT_IMPORTED} (FORMAT-14). */
    public RaceFormatTemplate importConfig(Actor actor, String name, RaceFormatConfig config) {
        return create(actor, "FORMAT_IMPORTED", "Imported the race format ", name, config);
    }
}
