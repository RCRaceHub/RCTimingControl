package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.domain.audiotemplate.AudioTemplate;
import dev.monkeypatch.rctiming.domain.audiotemplate.AudioTemplateService;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Audio templates under {@code /api/v1/race-control/settings/audio/templates}: list, save the current settings as a
 * new template, update a template from the current settings, apply one and delete one. RACE_DIRECTOR or ADMIN
 * only, and every change is audited. A bad name answers 400, a name already taken 409 and a missing template 404.
 */
@RestController
@RequestMapping("/api/v1/race-control/settings/audio/templates")
@DirectorOrAdmin
public class AudioTemplateController {

    private final AudioTemplateService service;

    public AudioTemplateController(AudioTemplateService service) {
        this.service = service;
    }

    /** A template and the settings it holds (never the club's own sound files). */
    public record AudioTemplateDto(Long id, String name, AudioSettingsController.AudioSettingsDto settings) {
        static AudioTemplateDto of(AudioTemplate t) {
            return new AudioTemplateDto(t.getId(), t.getName(), AudioSettingsController.AudioSettingsDto.of(t.getSettings()));
        }
    }

    /** The body of a save or an update: the template's name (on an update, left out to keep it). */
    public record AudioTemplateRequest(@Size(max = 80) String name) {
    }

    /** The body of a save: the template's name. */
    public record AudioTemplateCreateRequest(@NotBlank @Size(max = 80) String name) {
    }

    /** Every template, by name. {@code GET}. */
    @GetMapping
    public List<AudioTemplateDto> list() {
        return service.list().stream().map(AudioTemplateDto::of).toList();
    }

    /** Saves the settings in force now as a new template. {@code POST}; 201; 400; 409. Audited. */
    @Audited("audit_log")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AudioTemplateDto create(@RequestBody @Valid AudioTemplateCreateRequest request) {
        return AudioTemplateDto.of(service.createFromCurrent(CurrentOfficial.actor(), request.name()));
    }

    /** Replaces a template's settings with the ones in force now. {@code PUT /{id}}; 400; 404; 409. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    public AudioTemplateDto update(@PathVariable Long id,
                                   @RequestBody(required = false) @Valid AudioTemplateRequest request) {
        return AudioTemplateDto.of(service.overwriteWithCurrent(CurrentOfficial.actor(), id,
                request == null ? null : request.name()));
    }

    /**
     * Makes a template the club's audio settings and answers with the settings as saved. {@code POST /{id}/apply};
     * 404. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/apply")
    public AudioSettingsController.AudioSettingsDto apply(@PathVariable Long id) {
        return AudioSettingsController.AudioSettingsDto.of(service.apply(CurrentOfficial.actor(), id));
    }

    /** Deletes a template. {@code DELETE /{id}}; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentOfficial.actor(), id);
    }
}
