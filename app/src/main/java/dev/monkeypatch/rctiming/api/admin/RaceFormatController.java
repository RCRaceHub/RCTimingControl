package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.CreateRaceFormatTemplateRequest;
import dev.monkeypatch.rctiming.api.admin.dto.RaceFormatTemplateDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.format.RaceFormatCodec;
import dev.monkeypatch.rctiming.domain.format.RaceFormatCodec.FileType;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Race format templates under {@code /api/v1/admin/formats}: list, create, change, delete, and export or import as
 * JSON or YAML (FORMAT-14). Reads and export are open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN only
 * and audited. A template edit does not touch events that already use it, because each event class keeps its own
 * snapshot (FORMAT-06).
 */
@RestController
@RequestMapping("/api/v1/admin/formats")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class RaceFormatController {

    private final RaceFormatService raceFormatService;
    private final RaceFormatCodec codec;

    public RaceFormatController(RaceFormatService raceFormatService, RaceFormatCodec codec) {
        this.raceFormatService = raceFormatService;
        this.codec = codec;
    }

    /** Every template. {@code GET}; read only. */
    @GetMapping
    public List<RaceFormatTemplateDto> listFormats() {
        return raceFormatService.findAll().stream()
                .map(RaceFormatTemplateDto::from)
                .toList();
    }

    /** One template. {@code GET /{id}}; 404 if it does not exist. */
    @GetMapping("/{id}")
    public RaceFormatTemplateDto getFormat(@PathVariable Long id) {
        return RaceFormatTemplateDto.from(raceFormatService.findById(id));
    }

    /** Creates a template. {@code POST}; ADMIN; answers 201; 400 if the config is not valid. Audited. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RaceFormatTemplateDto createFormat(Authentication auth, @RequestBody @Valid CreateRaceFormatTemplateRequest request) {
        return RaceFormatTemplateDto.from(
                raceFormatService.create(CurrentOfficial.actor(auth), request.name(), request.config()));
    }

    /** Changes a template. {@code PUT /{id}}; ADMIN; 400 if the config is not valid. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RaceFormatTemplateDto updateFormat(Authentication auth, @PathVariable Long id,
                                               @RequestBody @Valid CreateRaceFormatTemplateRequest request) {
        return RaceFormatTemplateDto.from(
                raceFormatService.update(CurrentOfficial.actor(auth), id, request.name(), request.config()));
    }

    /**
     * Deletes a template. {@code DELETE /{id}}; ADMIN; 204; 409 if the database refuses because something still uses
     * it. Audited.
     */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFormat(Authentication auth, @PathVariable Long id) {
        raceFormatService.delete(CurrentOfficial.actor(auth), id);
    }

    /**
     * The template's config as a file, in the type the {@code Accept} header asks for: JSON (the default) or YAML.
     * {@code GET /{id}/export}; read only.
     */
    @GetMapping(value = "/{id}/export",
                produces = {MediaType.APPLICATION_JSON_VALUE, "application/yaml"})
    public ResponseEntity<String> exportFormat(
            @PathVariable Long id,
            @RequestHeader(value = "Accept", defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept) {
        FileType fileType = FileType.of(accept);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileType.mediaType()))
                .body(codec.write(raceFormatService.exportConfig(id), fileType));
    }

    /**
     * Creates a template from a JSON or YAML config in the body, named by {@code name}. {@code POST /import}; ADMIN;
     * answers 201; 400 if the body cannot be read or is not a valid config. Audited.
     */
    @Audited("audit_log")
    @PostMapping(value = "/import",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, "application/yaml"})
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RaceFormatTemplateDto importFormat(
            Authentication auth,
            @RequestParam(defaultValue = "Imported template") String name,
            @RequestBody String body,
            @RequestHeader("Content-Type") String contentType) {
        return RaceFormatTemplateDto.from(raceFormatService.importConfig(
                CurrentOfficial.actor(auth), name, codec.read(body, FileType.of(contentType))));
    }
}
