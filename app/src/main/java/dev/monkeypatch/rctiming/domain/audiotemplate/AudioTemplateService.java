package dev.monkeypatch.rctiming.domain.audiotemplate;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.club.ClubAudioSettings;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audio templates: named copies of the club's audio settings that race control can save and apply, for example
 * BRCA meeting, Club night or Practice. Called by {@code AudioTemplateController}; each change runs in one write
 * transaction with an audit row. A template holds the switches, wording and timings but never the club's own sound
 * files, so applying one leaves those alone. Applying goes through {@link ClubProfileService#changeAudioSettings}, so
 * it is audited like any settings change and remakes the pre-generated clips when the wording changed.
 */
@Service
@Transactional
public class AudioTemplateService {

    static final int MAX_NAME_LENGTH = 80;

    private final AudioTemplateRepository repository;
    private final ClubProfileService clubs;
    private final AuditService audit;

    public AudioTemplateService(AudioTemplateRepository repository, ClubProfileService clubs, AuditService audit) {
        this.repository = repository;
        this.clubs = clubs;
        this.audit = audit;
    }

    /** Every template, by name. */
    @ReadTransaction
    public List<AudioTemplate> list() {
        return repository.findAllByName();
    }

    /** One template by id. @throws EntityNotFoundException (404) if there is none */
    @ReadTransaction
    public AudioTemplate get(Long id) {
        return repository.getOrThrow(id);
    }

    /**
     * Saves the settings in force now under a new name and records {@code AUDIO_TEMPLATE_CREATED}.
     *
     * @throws IllegalArgumentException (400) if the name is blank or over 80 characters
     * @throws StateConflictException (409) if another template has the name (ignoring case)
     */
    public AudioTemplate createFromCurrent(Actor actor, String name) {
        AudioTemplate template = new AudioTemplate();
        template.setName(uniqueName(name, null));
        template.setSettings(clubs.audioSettings().withHornSounds(null));
        AudioTemplate saved = repository.save(template);
        audit.entry(actor, "AUDIO_TEMPLATE_CREATED").entity("audio_template", saved.getId())
                .summary("Saved the audio template " + saved.getName()).after(values(saved)).record();
        return saved;
    }

    /**
     * Replaces a template's settings with the ones in force now, and renames it when {@code name} is given, and
     * records {@code AUDIO_TEMPLATE_UPDATED}. Throws as {@link #createFromCurrent} does, and 404 if it is missing.
     */
    public AudioTemplate overwriteWithCurrent(Actor actor, Long id, String name) {
        AudioTemplate template = get(id);
        Map<String, Object> before = values(template);
        if (name != null && !name.isBlank()) {
            template.setName(uniqueName(name, id));
        }
        template.setSettings(clubs.audioSettings().withHornSounds(null));
        AudioTemplate saved = repository.save(template);
        audit.entry(actor, "AUDIO_TEMPLATE_UPDATED").entity("audio_template", id)
                .summary("Updated the audio template " + saved.getName()).before(before).after(values(saved)).record();
        return saved;
    }

    /**
     * Makes a template's settings the club's audio settings, keeping the club's own sound files, and records {@code
     * AUDIO_TEMPLATE_APPLIED} as well as the settings change itself. @throws EntityNotFoundException (404) if missing
     */
    public ClubAudioSettings apply(Actor actor, Long id) {
        AudioTemplate template = get(id);
        clubs.changeAudioSettings(actor, profile -> profile.setAudioSettings(
                template.getSettings().withHornSounds(profile.getAudioSettings().hornSounds())));
        audit.entry(actor, "AUDIO_TEMPLATE_APPLIED").entity("audio_template", id)
                .summary("Applied the audio template " + template.getName()).record();
        return clubs.audioSettings();
    }

    /** Removes a template and records {@code AUDIO_TEMPLATE_DELETED}. @throws EntityNotFoundException (404) if it is missing */
    public void delete(Actor actor, Long id) {
        AudioTemplate template = get(id);
        Map<String, Object> before = values(template);
        repository.deleteById(id);
        audit.entry(actor, "AUDIO_TEMPLATE_DELETED").entity("audio_template", id)
                .summary("Removed the audio template " + template.getName()).before(before).record();
    }

    private String uniqueName(String name, Long ownId) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("The name must be between 1 and " + MAX_NAME_LENGTH + " characters");
        }
        repository.findByName(clean).filter(other -> !other.getId().equals(ownId)).ifPresent(other -> {
            throw new StateConflictException("There is already an audio template called " + other.getName());
        });
        return clean;
    }

    private static Map<String, Object> values(AudioTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("settings", t.getSettings());
        return m;
    }
}
