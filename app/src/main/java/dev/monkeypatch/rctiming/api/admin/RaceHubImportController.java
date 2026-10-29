package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMapping;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imports RaceHub Entry Export v1 into an event, and manages its class mappings (L7, #15), under
 * {@code /api/v1/admin/events/{eventId}}. ADMIN only. Entries are upserted by entry id with the higher entry version
 * winning, and a withdrawn entry is kept, never deleted.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}")
@AdminOnly
public class RaceHubImportController {

    private final RaceHubImportService importService;

    public RaceHubImportController(RaceHubImportService importService) {
        this.importService = importService;
    }

    /**
     * Imports the export. With {@code dryRun=true} it returns the preview and saves nothing.
     * An import blocked by unmapped classes or invalid rows returns 422 with the preview.
     */
    @Audited("audit_log")
    @PostMapping("/racehub-import")
    public ResponseEntity<RaceHubImportResult> importEntries(@PathVariable Long eventId,
                                                             @RequestParam(defaultValue = "false") boolean dryRun,
                                                             @RequestBody RaceHubEntryExport export) {
        RaceHubImportResult result = importService.importEntries(CurrentOfficial.actor(), eventId, export, dryRun);
        return ImportResponses.of(result);
    }

    /** How RaceHub's event classes map to this event's classes. {@code GET /racehub-class-mappings}; read only. */
    @GetMapping("/racehub-class-mappings")
    public List<ClassMappingDto> listMappings(@PathVariable Long eventId) {
        return importService.listMappings(eventId).stream().map(ClassMappingDto::of).toList();
    }

    /**
     * Replaces all of the event's class mappings. {@code PUT /racehub-class-mappings}; 400 if a RaceHub class is
     * mapped twice. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/racehub-class-mappings")
    public List<ClassMappingDto> replaceMappings(@PathVariable Long eventId,
                                                 @RequestBody List<ClassMappingDto> mappings) {
        Map<String, Long> byRaceHubId = new LinkedHashMap<>();
        for (ClassMappingDto m : mappings) {
            if (byRaceHubId.put(m.racehubEventClassId(), m.eventClassId()) != null) {
                throw new IllegalArgumentException("RaceHub event class " + m.racehubEventClassId() + " is mapped twice");
            }
        }
        return importService.replaceMappings(CurrentOfficial.actor(), eventId, byRaceHubId).stream()
                .map(ClassMappingDto::of).toList();
    }

    /** One mapping, as sent and returned: a RaceHub event class id and the event class it lands in. */
    public record ClassMappingDto(String racehubEventClassId, Long eventClassId) {
        static ClassMappingDto of(RaceHubClassMapping m) {
            return new ClassMappingDto(m.getRacehubEventClassId(), m.getEventClassId());
        }
    }
}
