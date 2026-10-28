package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.user.OfficialService;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.query.official.OfficialChangeDto;
import dev.monkeypatch.rctiming.query.official.OfficialDto;
import dev.monkeypatch.rctiming.query.official.OfficialQueryService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * The Officials page (#61): add officials, change their roles, set a new password, and disable or
 * re-enable them. Admins only. Refusals that would lock the club out answer 409.
 */
@RestController
@RequestMapping("/api/v1/admin/officials")
@PreAuthorize("hasRole('ADMIN')")
public class OfficialController {

    /** How many changes a page of the history holds unless the request asks for fewer or more. */
    static final int CHANGES_PAGE = 50;
    /** The most a page may hold, whatever the request asks for. */
    static final int CHANGES_PAGE_MAX = 200;

    private final OfficialService officialService;
    private final OfficialQueryService officialQueryService;

    public OfficialController(OfficialService officialService, OfficialQueryService officialQueryService) {
        this.officialService = officialService;
        this.officialQueryService = officialQueryService;
    }

    /** The body of {@code POST}: a new official's email, name, password (8 or more characters) and roles. */
    public record AddOfficialRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(min = OfficialService.MIN_PASSWORD_LENGTH) String password,
            @NotEmpty Set<Role> roles
    ) {}

    /** The body of {@code PUT /{id}/roles}: the official's full set of roles, which replaces the old one. */
    public record RolesRequest(@NotEmpty Set<Role> roles) {}

    /** The body of {@code PUT /{id}/password}: the new password, 8 or more characters. */
    public record PasswordRequest(@NotNull @Size(min = OfficialService.MIN_PASSWORD_LENGTH) String password) {}

    /** Every official. {@code GET}; ADMIN; read only. */
    @GetMapping
    public List<OfficialDto> list() {
        return officialQueryService.listAll();
    }

    /**
     * The history of changes to officials, newest first. Pass the id of the oldest change shown as {@code before} to
     * get the page after it. {@code GET /changes}; ADMIN; {@code size} is kept between 1 and 200.
     */
    @GetMapping("/changes")
    public List<OfficialChangeDto> changes(@RequestParam(required = false) Long before,
                                           @RequestParam(defaultValue = "" + CHANGES_PAGE) int size) {
        return officialQueryService.changes(before, Math.min(Math.max(size, 1), CHANGES_PAGE_MAX));
    }

    /**
     * Adds an official. {@code POST}; ADMIN; answers 201. 400 for a bad body; 409 if the change is refused (such as
     * an email already in use). Recorded in the officials audit log.
     */
    @Audited("official_audit_log")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficialDto add(@RequestBody @Valid AddOfficialRequest request) {
        User user = officialService.add(request.email(), request.firstName(), request.lastName(),
                request.password(), request.roles(), CurrentOfficial.id());
        return toDto(user);
    }

    /**
     * Replaces an official's roles. {@code PUT /{id}/roles}; ADMIN. 409 if it would leave the club without an
     * enabled admin. Recorded in the officials audit log.
     */
    @Audited("official_audit_log")
    @PutMapping("/{id}/roles")
    public OfficialDto changeRoles(@PathVariable long id, @RequestBody @Valid RolesRequest request) {
        return toDto(officialService.changeRoles(id, request.roles(), CurrentOfficial.id()));
    }

    /**
     * Sets a new password. {@code PUT /{id}/password}; ADMIN; 204. The official's refresh tokens are revoked, so
     * they are signed out when their access token runs out. Recorded in the officials audit log.
     */
    @Audited("official_audit_log")
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable long id, @RequestBody @Valid PasswordRequest request) {
        officialService.setPassword(id, request.password(), CurrentOfficial.id());
    }

    /**
     * Disables an official, who can no longer sign in. {@code POST /{id}/disable}; ADMIN. 409 for the last enabled
     * admin or for an admin disabling themselves. Their sessions are ended. Recorded in the officials audit log.
     */
    @Audited("official_audit_log")
    @PostMapping("/{id}/disable")
    public OfficialDto disable(@PathVariable long id) {
        return toDto(officialService.disable(id, CurrentOfficial.id()));
    }

    /** Enables a disabled official again. {@code POST /{id}/enable}; ADMIN. Recorded in the officials audit log. */
    @Audited("official_audit_log")
    @PostMapping("/{id}/enable")
    public OfficialDto enable(@PathVariable long id) {
        return toDto(officialService.enable(id, CurrentOfficial.id()));
    }

    private static OfficialDto toDto(User user) {
        return new OfficialDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.isEnabled(), user.getDisabledAt(), user.getCreatedAt());
    }
}
