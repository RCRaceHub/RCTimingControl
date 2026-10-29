package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.query.audit.AuditFilter;
import dev.monkeypatch.rctiming.query.audit.AuditPageDto;
import dev.monkeypatch.rctiming.query.audit.AuditQueryService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Reads the audit log (#138), under {@code /api/v1/admin/audit}. Admin only, since it shows who did what, including
 * sign-in attempts.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@AdminOnly
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /**
     * Searches the audit log, newest first. {@code GET}; ADMIN. Every filter is optional; the ones given must all
     * match. {@code from} and {@code to} are ISO date-times; {@code page} counts from 0 and {@code size} (default 50)
     * is capped by the query service. Read only.
     */
    @GetMapping
    public AuditPageDto search(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long raceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditQueryService.search(
                new AuditFilter(entityType, entityId, action, actorUserId, eventId, raceId, from, to), page, size);
    }
}
