package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.api.admin.dto.AdminCreateEntryRequest;
import dev.monkeypatch.rctiming.api.admin.dto.AdminWithdrawRequest;
import dev.monkeypatch.rctiming.api.admin.dto.EntryDto;
import dev.monkeypatch.rctiming.api.admin.dto.EntryResult;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryService;
import dev.monkeypatch.rctiming.query.entry.AdminEntryDto;
import dev.monkeypatch.rctiming.query.entry.AdminEntryQueryService;
import dev.monkeypatch.rctiming.query.entry.EntryHistoryDto;
import dev.monkeypatch.rctiming.query.entry.EntryHistoryQuery;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Entries under {@code /api/v1/admin/entries}: the list for an event's class, an entry's history, walk-ins and
 * withdrawals. The reads are open to ADMIN, RACE_DIRECTOR and REFEREE; adding a walk-in and withdrawing need
 * RACE_DIRECTOR or ADMIN, and the history is ADMIN only. Racers do not enter here: entries arrive from the RaceHub
 * import or as walk-ins. Changes are recorded in the entry audit log.
 */
@RestController
@RequestMapping("/api/v1/admin/entries")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class AdminEntryController {

    private final EntryService entryService;
    private final AdminEntryQueryService adminEntryQueryService;
    private final EntryHistoryQuery entryHistoryQuery;
    private final EntryRepository entryRepository;

    public AdminEntryController(EntryService entryService,
                                AdminEntryQueryService adminEntryQueryService,
                                EntryHistoryQuery entryHistoryQuery,
                                EntryRepository entryRepository) {
        this.entryService = entryService;
        this.adminEntryQueryService = adminEntryQueryService;
        this.entryHistoryQuery = entryHistoryQuery;
        this.entryRepository = entryRepository;
    }

    /** The entries in one class of an event. {@code GET /events/{eventId}/classes/{classId}}; read only. */
    @GetMapping("/events/{eventId}/classes/{classId}")
    public List<AdminEntryDto> listEntriesForClass(@PathVariable Long eventId,
                                                    @PathVariable Long classId) {
        return adminEntryQueryService.listEntriesForClass(eventId, classId);
    }

    /**
     * What has happened to one entry, oldest first (#140). Admins only: it names the officials who acted.
     * {@code GET /{id}/history}; 404 if the entry does not exist.
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EntryHistoryDto> getHistory(@PathVariable long id) {
        entryRepository.requireExists(id);
        return entryHistoryQuery.forEntry(id);
    }

    /**
     * Adds a walk-in entry by hand (L9, #17). {@code POST}; RACE_DIRECTOR or ADMIN; answers 201 with the entry and
     * any warnings, such as a transponder another entry in the event already uses. Name an existing competitor or a
     * new one, not both. 409 if the event is completed, or the new name matches someone already there and the
     * request does not confirm it is a different person; 404 for an unknown event, class or competitor; 400 for a
     * class not in the event or a secondary transponder equal to the primary. Class membership rules are not
     * checked. Recorded in the entry audit log.
     */
    @Audited("entry_audit_log")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public EntryResult createEntry(Authentication auth, @RequestBody @Valid AdminCreateEntryRequest req) {
        EntryService.WalkInResult result = entryService.adminCreateEntry(CurrentOfficial.id(auth),
                new EntryService.WalkIn(req.eventId(), req.eventClassId(), req.competitorId(), req.competitorName(),
                        req.primaryTransponder(), req.secondaryTransponder(), req.confirmNewCompetitor(),
                        req.thirdTransponder(), req.fourthTransponder()));
        return new EntryResult(EntryDto.from(result.entry()), result.warnings());
    }

    /**
     * Withdraws an entry; it is kept, never deleted. {@code POST /{id}/withdraw}; RACE_DIRECTOR or ADMIN. 404 if the
     * entry does not exist; 409 if it is already withdrawn. The reason is stored on the entry audit row.
     */
    @Audited("entry_audit_log")
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public EntryDto withdrawEntry(@PathVariable Long id,
                                  Authentication auth,
                                  @RequestBody @Valid AdminWithdrawRequest req) {
        return EntryDto.from(entryService.adminWithdraw(id, CurrentOfficial.id(auth), req.reason()));
    }
}
