package dev.monkeypatch.rctiming.api.racecontrol;

import dev.monkeypatch.rctiming.api.racecontrol.dto.TransponderSwapRequest;
import dev.monkeypatch.rctiming.api.racecontrol.dto.TransponderSwapResponse;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.query.racecontrol.EntrySearchDto;
import dev.monkeypatch.rctiming.query.racecontrol.EntrySearchQuery;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Entry search and transponder swap for race control, under {@code /api/v1/race-control/events/{eventId}}. Open to
 * every official (RACE_DIRECTOR, REFEREE and ADMIN).
 */
@RestController
@RequestMapping("/api/v1/race-control/events/{eventId}")
@PreAuthorize("hasAnyRole('RACE_DIRECTOR','REFEREE','ADMIN')")
public class TransponderSwapController {

    /** What each refusal code means, for the error's detail. */
    private static final Map<String, String> MESSAGES = Map.of(
            "entry_not_found", "No such entry in this event",
            "entry_withdrawn", "The entry has been withdrawn",
            "transponder_already_assigned", "Another competitor already has that transponder in this event",
            "same_as_other_transponder", "The transponder is already this entry's other transponder",
            "primary_required", "An entry must keep a primary transponder");

    private final EntrySearchQuery entrySearchQuery;
    private final TransponderSwapService transponderSwapService;

    public TransponderSwapController(EntrySearchQuery entrySearchQuery,
                                     TransponderSwapService transponderSwapService) {
        this.entrySearchQuery = entrySearchQuery;
        this.transponderSwapService = transponderSwapService;
    }

    /**
     * Entries whose competitor name contains the query, to find the entry to swap.
     * {@code GET /entries/search?query=}; read only.
     */
    @GetMapping("/entries/search")
    public List<EntrySearchDto> search(@PathVariable long eventId,
                                        @RequestParam(defaultValue = "") String query) {
        return entrySearchQuery.searchByName(eventId, query);
    }

    /**
     * Swaps (or, for any slot but the primary, removes) a transponder on an entry on the day.
     * {@code POST /entries/{entryId}/transponder-swap}. Answers 200 with a {@link TransponderSwapResponse}, or a
     * problem with an {@code error} code: {@code entry_not_found} (404), {@code entry_withdrawn} and
     * {@code transponder_already_assigned} (409, another competitor holds the number in the event),
     * {@code same_as_other_transponder} and {@code primary_required} (400). Recorded in the entry audit log.
     */
    @Audited("entry_audit_log")
    @PostMapping("/entries/{entryId}/transponder-swap")
    public ResponseEntity<?> swapTransponder(@PathVariable long eventId,
                                             @PathVariable long entryId,
                                             @Valid @RequestBody TransponderSwapRequest request) {
        SwapResult result = transponderSwapService.swap(
                eventId, entryId, request.slot(), request.newTransponderNumber(), CurrentOfficial.id());
        return switch (result) {
            case SwapResult.Success s -> ResponseEntity.ok(new TransponderSwapResponse(
                    entryId, s.slot(), s.oldNumber(), s.newNumber()));
            case SwapResult.EntryNotFound ignored -> error(HttpStatus.NOT_FOUND, "entry_not_found");
            case SwapResult.EntryWithdrawn ignored -> error(HttpStatus.CONFLICT, "entry_withdrawn");
            case SwapResult.TransponderAlreadyAssigned ignored ->
                    error(HttpStatus.CONFLICT, "transponder_already_assigned");
            case SwapResult.SameAsOtherSlot ignored ->
                    error(HttpStatus.BAD_REQUEST, "same_as_other_transponder");
            case SwapResult.PrimaryRequired ignored -> error(HttpStatus.BAD_REQUEST, "primary_required");
        };
    }

    /**
     * A refusal as a ProblemDetail, like every other error the API gives, keeping the {@code error} code the
     * transponder swap has always had.
     */
    private static ResponseEntity<?> error(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, MESSAGES.getOrDefault(code, code));
        detail.setProperty("error", code);
        return ResponseEntity.status(status).body(detail);
    }
}
