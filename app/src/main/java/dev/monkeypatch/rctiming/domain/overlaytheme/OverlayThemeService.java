package dev.monkeypatch.rctiming.domain.overlaytheme;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Saved overlay themes: the named looks of the streaming overlay. Called by {@code AdminOverlayThemeController}
 * (changes, each in one write transaction with an audit row) and by {@code PublicOverlayThemeController} (reads, so
 * the overlay in OBS can fetch its theme without signing in). The design is stored as the JSON the designer wrote; this
 * service only checks that it is a JSON object of reasonable size, since the overlay ignores what it does not know.
 */
@Service
@Transactional
public class OverlayThemeService {

    /** The longest design text accepted, in characters; a real design is under 1 KB. */
    static final int MAX_DESIGN_LENGTH = 8000;

    private final OverlayThemeRepository repository;
    private final AuditService audit;
    private final ObjectMapper json;

    public OverlayThemeService(OverlayThemeRepository repository, AuditService audit, ObjectMapper json) {
        this.repository = repository;
        this.audit = audit;
        this.json = json;
    }

    /** Every theme, by name. */
    @ReadTransaction
    public List<OverlayTheme> list() {
        return repository.findAllByName();
    }

    /** One theme by id. @throws EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public OverlayTheme get(Long id) {
        return repository.getOrThrow(id);
    }

    /**
     * The club's default theme, the look the plain overlay address opens. Empty when none is set, so the overlay then
     * uses the options in its own address.
     */
    @ReadTransaction
    public Optional<OverlayTheme> getDefault() {
        return repository.findDefault();
    }

    /**
     * Adds a theme and records {@code OVERLAY_THEME_CREATED}.
     *
     * @throws IllegalArgumentException (400) for a blank or over-long name, or a design that is not a JSON object
     * @throws StateConflictException (409) if the name is taken
     */
    public OverlayTheme create(Actor actor, String name, JsonNode design) {
        OverlayTheme theme = new OverlayTheme();
        fill(theme, name, design);
        OverlayTheme saved = repository.save(theme);
        audit.entry(actor, "OVERLAY_THEME_CREATED").entity("overlay_theme", saved.getId())
                .summary("Added the overlay theme " + saved.getName()).after(values(saved)).record();
        return saved;
    }

    /** Changes a theme and records {@code OVERLAY_THEME_UPDATED}; throws as {@link #create} does, and 404 if it is missing. */
    public OverlayTheme update(Actor actor, Long id, String name, JsonNode design) {
        OverlayTheme theme = get(id);
        Map<String, Object> before = values(theme);
        fill(theme, name, design);
        OverlayTheme saved = repository.save(theme);
        audit.entry(actor, "OVERLAY_THEME_UPDATED").entity("overlay_theme", id)
                .summary("Changed the overlay theme " + saved.getName()).before(before).after(values(saved)).record();
        return saved;
    }

    /**
     * Makes a theme the club's default and records {@code OVERLAY_THEME_DEFAULT_SET}; any earlier default stops being
     * one in the same transaction, so there is never more than one. A club has one database, so the default is
     * club-wide. @throws EntityNotFoundException (404) if the theme is missing
     */
    public OverlayTheme setDefault(Actor actor, Long id) {
        OverlayTheme theme = get(id);
        if (theme.getIsDefault()) {
            return theme;
        }
        repository.findDefault().ifPresent(previous -> {
            previous.setIsDefault(false);
            repository.save(previous);
        });
        theme.setIsDefault(true);
        OverlayTheme saved = repository.save(theme);
        audit.entry(actor, "OVERLAY_THEME_DEFAULT_SET").entity("overlay_theme", id)
                .summary("Made the overlay theme " + saved.getName() + " the default").record();
        return saved;
    }

    /** Stops any theme being the default and records {@code OVERLAY_THEME_DEFAULT_CLEARED}; does nothing if none is. */
    public void clearDefault(Actor actor) {
        repository.findDefault().ifPresent(previous -> {
            previous.setIsDefault(false);
            repository.save(previous);
            audit.entry(actor, "OVERLAY_THEME_DEFAULT_CLEARED").entity("overlay_theme", previous.getId())
                    .summary("Cleared the default overlay theme " + previous.getName()).record();
        });
    }

    /** Removes a theme and records {@code OVERLAY_THEME_DELETED}. @throws EntityNotFoundException (404) if it is missing */
    public void delete(Actor actor, Long id) {
        OverlayTheme theme = get(id);
        Map<String, Object> before = values(theme);
        repository.deleteById(id);
        audit.entry(actor, "OVERLAY_THEME_DELETED").entity("overlay_theme", id)
                .summary("Removed the overlay theme " + theme.getName()).before(before).record();
    }

    /** A theme's design as JSON, for the DTOs; touches no table, so it opens no transaction. */
    @Transactional(propagation = Propagation.SUPPORTS)
    public JsonNode designOf(OverlayTheme theme) {
        try {
            return json.readTree(theme.getDesign());
        } catch (JacksonException e) {
            throw new IllegalStateException("The design of overlay theme " + theme.getName() + " cannot be read", e);
        }
    }

    private void fill(OverlayTheme theme, String name, JsonNode design) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 100) {
            throw new IllegalArgumentException("A theme needs a name of up to 100 characters");
        }
        if (design == null || !design.isObject()) {
            throw new IllegalArgumentException("A theme's design must be a JSON object");
        }
        String text;
        try {
            text = json.writeValueAsString(design);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The design cannot be written", e);
        }
        if (text.length() > MAX_DESIGN_LENGTH) {
            throw new IllegalArgumentException("The design is too large");
        }
        repository.findByName(clean).filter(other -> !other.getId().equals(theme.getId())).ifPresent(other -> {
            throw new StateConflictException("There is already an overlay theme called " + clean);
        });
        theme.setName(clean);
        theme.setDesign(text);
    }

    private static Map<String, Object> values(OverlayTheme t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("design", t.getDesign());
        m.put("default", t.getIsDefault());
        return m;
    }
}
