package dev.monkeypatch.rctiming.domain.format;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RaceFormatService {

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
     * the snapshot is returned directly.
     */
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
        };
        return minutes > 0 ? minutes * 60_000L : null;
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
                };
        if (fromFormat != null) {
            return fromFormat;
        }
        return isFinal ? StartType.GRID : StartType.STAGGER;
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

    @Transactional(readOnly = true)
    public List<RaceFormatTemplate> findAll() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public RaceFormatTemplate findById(Long id) {
        return templateRepository.getOrThrow(id);
    }

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

    public RaceFormatConfig exportConfig(Long id) {
        return findById(id).getConfig();
    }

    public RaceFormatTemplate importConfig(Actor actor, String name, RaceFormatConfig config) {
        return create(actor, "FORMAT_IMPORTED", "Imported the race format ", name, config);
    }
}
