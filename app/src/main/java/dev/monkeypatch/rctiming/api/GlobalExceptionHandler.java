package dev.monkeypatch.rctiming.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorMergeRefusedException;
import dev.monkeypatch.rctiming.domain.competitor.PossibleDuplicateCompetitorException;
import dev.monkeypatch.rctiming.domain.user.OfficialChangeRefusedException;
import dev.monkeypatch.rctiming.backup.BackupFailedException;
import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns exceptions from controllers and services into {@link ProblemDetail} JSON. Most 409 bodies are plain
 * text for the page to show. Two carry a {@code code} the page acts on: {@code POSSIBLE_DUPLICATE_COMPETITOR}
 * (offer the matching competitors instead of creating a new one) and {@code COMPETITOR_MERGE_REFUSED} (say
 * why two competitors cannot be merged).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** A typed walk-in name matches an existing competitor: say who, so the official can choose (#123). */
    @ExceptionHandler(PossibleDuplicateCompetitorException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handlePossibleDuplicate(PossibleDuplicateCompetitorException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setProperty("code", "POSSIBLE_DUPLICATE_COMPETITOR");
        List<Map<String, Object>> matches = ex.getMatches().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("displayName", c.getDisplayName());
            m.put("brcaNumber", c.getBrcaNumber());
            m.put("homeClub", c.getHomeClub());
            m.put("spokenName", c.getSpokenName());
            return m;
        }).toList();
        detail.setProperty("matches", matches);
        return detail;
    }

    /** Two competitors can't be merged as they are; say why (#123). */
    @ExceptionHandler(CompetitorMergeRefusedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleMergeRefused(CompetitorMergeRefusedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setProperty("code", "COMPETITOR_MERGE_REFUSED");
        detail.setProperty("blockers", ex.getBlockers());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a)));
        return detail;
    }

    /**
     * A request body that could not be read, such as a value that is not one of an enum's. The parser's own message
     * names classes, so only the field and the values it takes are passed on.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()
                && !invalid.getPath().isEmpty()) {
            String field = invalid.getPath().get(invalid.getPath().size() - 1).getFieldName();
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, field + " must be one of "
                    + Arrays.toString(invalid.getTargetType().getEnumConstants()) + ", got: " + invalid.getValue());
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body could not be read");
    }

    /** A unique value is taken, such as a second racing class with the same name. */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleDuplicate(DuplicateKeyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Resource already exists");
    }

    /** Any other broken data rule, most often deleting something that is still used elsewhere. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The change conflicts with other data: something still uses it, or it refers to something missing");
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleStateTransition(IllegalStateTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** The thing is in the wrong state for the request, such as a practice session that is already running. */
    @ExceptionHandler(StateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleStateConflict(StateConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OfficialChangeRefusedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleOfficialChangeRefused(OfficialChangeRefusedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** A controller refused with a status of its own, such as the announcer voice not being installed. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
    }

    /**
     * A {@code @PreAuthorize} refusal. It is thrown inside Spring MVC, after the security filters have let the
     * request through, so the filter chain's handler never sees it; without this it would be a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    /** A controller asked for the signed-in official and there was none (security/CurrentOfficial). */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleNotSignedIn(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Not signed in");
    }

    @ExceptionHandler(DateTimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleDateTimeException(DateTimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid date or time: " + ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BackupFailedException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleBackupFailed(BackupFailedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
}
