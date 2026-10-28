package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.CloneScoringSystemRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScoringSystemDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScoringSystemRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.format.RaceFormatCodec.FileType;
import dev.monkeypatch.rctiming.domain.scoring.ScoringCodec;
import dev.monkeypatch.rctiming.domain.scoring.ScoringKind;
import dev.monkeypatch.rctiming.domain.scoring.ScoringSystemService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Scoring systems under {@code /api/v1/admin/scoring-systems}: list, read, create, clone, change, delete, and export or
 * import as JSON or YAML. Reads and export are open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN only and
 * audited. Built-in systems are locked: changing or deleting one answers 409 and a clone is how it is adapted.
 */
@RestController
@RequestMapping("/api/v1/admin/scoring-systems")
@RaceControlStaff
public class ScoringSystemController {

    private final ScoringSystemService service;
    private final ScoringCodec codec;

    public ScoringSystemController(ScoringSystemService service, ScoringCodec codec) {
        this.service = service;
        this.codec = codec;
    }

    /** Every system, or one kind's when {@code kind} is {@code RACE} or {@code CHAMPIONSHIP}. {@code GET}; read only. */
    @GetMapping
    public List<ScoringSystemDto> list(@RequestParam(required = false) ScoringKind kind) {
        return service.list(kind).stream().map(ScoringSystemDto::from).toList();
    }

    /** One system. {@code GET /{id}}; 404 if it does not exist. */
    @GetMapping("/{id}")
    public ScoringSystemDto get(@PathVariable Long id) {
        return ScoringSystemDto.from(service.get(id));
    }

    /** Creates a system. {@code POST}; ADMIN; 201; 400 if a value is out of range; 409 if the name is taken. Audited. */
    @Audited("audit_log")
    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ScoringSystemDto create(@RequestBody @Valid ScoringSystemRequest request) {
        return ScoringSystemDto.from(service.create(CurrentOfficial.actor(), request.name(), request.config()));
    }

    /** Copies a system, built-in or not, under a new name. {@code POST /{id}/clone}; ADMIN; 201; 404; 409. Audited. */
    @Audited("audit_log")
    @PostMapping("/{id}/clone")
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ScoringSystemDto cloneSystem(@PathVariable Long id,
                                        @RequestBody @Valid CloneScoringSystemRequest request) {
        return ScoringSystemDto.from(service.cloneSystem(CurrentOfficial.actor(), id, request.name()));
    }

    /** Changes a custom system. {@code PUT /{id}}; ADMIN; 400 for a bad config; 409 for a built-in. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @AdminOnly
    public ScoringSystemDto update(@PathVariable Long id,
                                   @RequestBody @Valid ScoringSystemRequest request) {
        return ScoringSystemDto.from(service.update(CurrentOfficial.actor(), id, request.name(), request.config()));
    }

    /** Deletes a custom system. {@code DELETE /{id}}; ADMIN; 204; 409 for a built-in. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentOfficial.actor(), id);
    }

    /**
     * The system's config as a file, in the type the {@code Accept} header asks for: JSON (the default) or YAML.
     * {@code GET /{id}/export}; read only.
     */
    @GetMapping(value = "/{id}/export", produces = {MediaType.APPLICATION_JSON_VALUE, "application/yaml"})
    public ResponseEntity<String> export(
            @PathVariable Long id,
            @RequestHeader(value = "Accept", defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept) {
        FileType fileType = FileType.of(accept);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileType.mediaType()))
                .body(codec.write(service.get(id).getConfig(), fileType));
    }

    /**
     * Creates a system from a JSON or YAML config in the body, named by {@code name}. {@code POST /import}; ADMIN only,
     * as for formats; 201; 400 if the body cannot be read or a value is out of range. Audited.
     */
    @Audited("audit_log")
    @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/yaml"})
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ScoringSystemDto importSystem(@RequestParam(defaultValue = "Imported scoring system") String name,
            @RequestBody String body,
            @RequestHeader("Content-Type") String contentType) {
        return ScoringSystemDto.from(service.importConfig(
                CurrentOfficial.actor(), name, codec.read(body, FileType.of(contentType))));
    }
}
