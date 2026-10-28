package dev.monkeypatch.rctiming.domain.scoring;

import dev.monkeypatch.rctiming.domain.Names;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoring systems: a named, reusable set of rules for scoring races or a championship. Called by {@code
 * ScoringSystemController}; every method that changes data runs in one write transaction and records an audit row.
 * Built-in systems are locked and a clone is how one is changed. A race format picks a race system and
 * copies its values, and so does each event class it is given to, so a later edit never changes what is already set
 * up; championships will pick one in a later change.
 */
@Service
@Transactional
public class ScoringSystemService {

    private final ScoringSystemRepository repository;
    private final AuditService audit;
    private final RaceFormatService raceFormats;

    public ScoringSystemService(ScoringSystemRepository repository, AuditService audit, RaceFormatService raceFormats) {
        this.repository = repository;
        this.audit = audit;
        this.raceFormats = raceFormats;
    }

    /** Every system, or only those of {@code kind} when it is given; built-ins first. */
    @ReadTransaction
    public List<ScoringSystem> list(ScoringKind kind) {
        if (kind != null) {
            return repository.findByKind(kind);
        }
        return Arrays.stream(ScoringKind.values()).flatMap(k -> repository.findByKind(k).stream()).toList();
    }

    /** One system. @throws dev.monkeypatch.rctiming.domain.EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public ScoringSystem get(Long id) {
        return repository.getOrThrow(id);
    }

    /**
     * Adds a custom system; its kind comes from the config. Records {@code SCORING_SYSTEM_CREATED}.
     *
     * @throws IllegalArgumentException (400) if a value is out of range
     * @throws org.springframework.dao.DuplicateKeyException (409) if the kind already has a system of that name
     */
    public ScoringSystem create(Actor actor, String name, ScoringConfig config) {
        return add(actor, "SCORING_SYSTEM_CREATED", "Added the scoring system ", name, config);
    }

    /** Adds a system from an imported file's config and records {@code SCORING_SYSTEM_IMPORTED}. */
    public ScoringSystem importConfig(Actor actor, String name, ScoringConfig config) {
        return add(actor, "SCORING_SYSTEM_IMPORTED", "Imported the scoring system ", name, config);
    }

    /**
     * Copies a system, built-in or not, under a new name as a custom one that can be edited. Records {@code
     * SCORING_SYSTEM_CLONED}.
     */
    public ScoringSystem cloneSystem(Actor actor, Long id, String name) {
        ScoringSystem source = get(id);
        return add(actor, "SCORING_SYSTEM_CLONED", "Cloned the scoring system " + source.getName() + " as ", name,
                source.getConfig());
    }

    private ScoringSystem add(Actor actor, String action, String summary, String name, ScoringConfig config) {
        config.validate();
        ScoringSystem system = new ScoringSystem();
        system.setKind(config.kind());
        system.setName(cleanName(name));
        requireFreeName(config.kind(), system.getName(), null);
        system.setConfig(config);
        ScoringSystem saved = repository.save(system);
        audit.entry(actor, action).entity("scoring_system", saved.getId())
                .summary(summary + saved.getName())
                .after(values(saved)).record();
        return saved;
    }

    /**
     * Changes a custom system's name and rules. The kind cannot change. Records {@code SCORING_SYSTEM_UPDATED}.
     *
     * @throws StateConflictException (409) for a built-in system, which is locked
     * @throws IllegalArgumentException (400) if the config is of another kind or a value is out of range
     */
    public ScoringSystem update(Actor actor, Long id, String name, ScoringConfig config) {
        ScoringSystem system = get(id);
        requireCustom(system, "changed");
        if (config.kind() != system.getKind()) {
            throw new IllegalArgumentException("A " + system.getKind() + " scoring system cannot take " + config.kind() + " rules");
        }
        config.validate();
        Map<String, Object> before = values(system);
        system.setName(cleanName(name));
        requireFreeName(system.getKind(), system.getName(), id);
        system.setConfig(config);
        ScoringSystem saved = repository.save(system);
        audit.entry(actor, "SCORING_SYSTEM_UPDATED").entity("scoring_system", id)
                .summary("Changed the scoring system " + saved.getName())
                .before(before).after(values(saved)).record();
        return saved;
    }

    /**
     * Removes a custom system and records {@code SCORING_SYSTEM_DELETED}. Event classes keep their own copy of the
     * values, so only the format templates that are set from it stop a delete.
     *
     * @throws StateConflictException (409) for a built-in system or one a format template uses
     */
    public void delete(Actor actor, Long id) {
        ScoringSystem system = get(id);
        requireCustom(system, "deleted");
        if (raceFormats.isScoringSystemInUse(id)) {
            throw new StateConflictException("The scoring system " + system.getName() + " is used by a race format; change that format first");
        }
        Map<String, Object> before = values(system);
        repository.deleteById(id);
        audit.entry(actor, "SCORING_SYSTEM_DELETED").entity("scoring_system", id)
                .summary("Removed the scoring system " + system.getName())
                .before(before).record();
    }

    /** The name without outer spaces. @throws IllegalArgumentException (400) if it is blank or over 255 characters */
    private static String cleanName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 255) {
            throw new IllegalArgumentException("The name must be between 1 and 255 characters");
        }
        return clean;
    }

    /** Names are unique per kind without regard to case or spacing, as {@link Names#matchKey} compares them. */
    private void requireFreeName(ScoringKind kind, String name, Long ownId) {
        String key = Names.matchKey(name);
        boolean taken = repository.findByKind(kind).stream()
                .anyMatch(s -> !s.getId().equals(ownId) && Names.matchKey(s.getName()).equals(key));
        if (taken) {
            throw new StateConflictException("There is already a " + kind.name().toLowerCase() + " scoring system called " + name);
        }
    }

    private static void requireCustom(ScoringSystem system, String verb) {
        if (system.isBuiltIn()) {
            throw new StateConflictException(
                    "The built-in scoring system " + system.getName() + " is locked and cannot be " + verb + "; clone it to make a version you can change");
        }
    }

    private static Map<String, Object> values(ScoringSystem s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", s.getKind());
        m.put("name", s.getName());
        m.put("config", s.getConfig());
        return m;
    }
}
