package dev.monkeypatch.rctiming.api.setup;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.api.auth.AuthResponse;
import dev.monkeypatch.rctiming.api.setup.dto.BootstrapRequest;
import dev.monkeypatch.rctiming.api.setup.dto.DecoderConfigDto;
import dev.monkeypatch.rctiming.api.setup.dto.DecoderConfigUpdateRequest;
import dev.monkeypatch.rctiming.api.setup.dto.SetupProgressDto;
import dev.monkeypatch.rctiming.api.setup.dto.SetupStaffRequest;
import dev.monkeypatch.rctiming.api.setup.dto.SetupStatusDto;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.DecoderSettings;
import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import dev.monkeypatch.rctiming.service.SetupService;
import dev.monkeypatch.rctiming.timing.DecoderProbe;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The first-run setup wizard under {@code /api/v1/setup}. {@code GET /status} and {@code POST /bootstrap} are open
 * to anyone ({@code SecurityConfig}) so a fresh install can create its first admin; every other route needs the
 * {@code ADMIN} role.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;
    private final ClubProfileService clubProfileService;
    private final UserService userService;
    private final DecoderProbe decoderProbe;
    private final JwtTokenService jwtTokenService;

    public SetupController(SetupService setupService,
                           ClubProfileService clubProfileService,
                           UserService userService,
                           DecoderProbe decoderProbe,
                           JwtTokenService jwtTokenService) {
        this.setupService = setupService;
        this.clubProfileService = clubProfileService;
        this.userService = userService;
        this.decoderProbe = decoderProbe;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Whether the install has an account yet and whether the club step is done, so the page knows whether to show
     * the wizard. {@code GET /api/v1/setup/status}, anonymous.
     */
    @GetMapping("/status")
    public SetupStatusDto getStatus() {
        return SetupStatusDto.from(setupService.getStatus());
    }

    /**
     * Creates the first admin account and signs it in: {@code POST /api/v1/setup/bootstrap}, anonymous. The only
     * guard is that no account exists yet, so the first caller on the venue network becomes admin. Answers 201 with
     * an {@link AuthResponse}; 409 once anyone has an account; 400 for a bad body. Recorded in the officials audit
     * log with no actor. Unlike sign-in, it sets no refresh cookie.
     */
    @Audited("official_audit_log")
    @PostMapping("/bootstrap")
    public ResponseEntity<AuthResponse> bootstrap(@RequestBody @Valid BootstrapRequest req) {
        // Already set up: StateConflictException, answered 409 by GlobalExceptionHandler
        User admin = setupService.bootstrap(req.email(), req.password(), req.firstName(), req.lastName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AuthResponse.of(admin, jwtTokenService.generateAccessToken(admin)));
    }

    /**
     * Which wizard steps are done (club, track, format, staff, decoder). {@code GET /api/v1/setup/progress},
     * {@code ADMIN}.
     */
    @GetMapping("/progress")
    @PreAuthorize("hasRole('ADMIN')")
    public SetupProgressDto getProgress() {
        return SetupProgressDto.from(setupService.getProgress());
    }

    /** The decoder address saved in the club profile. {@code GET /api/v1/setup/decoder-config}, {@code ADMIN}. */
    @GetMapping("/decoder-config")
    @PreAuthorize("hasRole('ADMIN')")
    public DecoderConfigDto getDecoderConfig() {
        DecoderSettings settings = clubProfileService.getDecoderSettings();
        return new DecoderConfigDto(settings.host(), settings.port(), settings.protocol());
    }

    /**
     * Tests the decoder address on the form. Does not save it or change the live listener.
     * {@code POST /api/v1/setup/decoder-config/test}, {@code ADMIN}; 400 for a bad body.
     */
    @PostMapping("/decoder-config/test")
    @PreAuthorize("hasRole('ADMIN')")
    public DecoderProbe.Result testDecoderConfig(@RequestBody @Valid DecoderConfigUpdateRequest req) {
        return decoderProbe.probe(req.decoderHost(), req.decoderPort(), req.decoderProtocol());
    }

    /**
     * Saves the decoder address in the club profile and returns the wizard's progress.
     * {@code PATCH /api/v1/setup/decoder-config}, {@code ADMIN}; 400 for a bad body. Recorded in the audit log with
     * the old and new values, and announces the change so the decoder listener can pick up the new address. The
     * club step has to be done first, since the address lives in the club profile.
     */
    @Audited("audit_log")
    @PatchMapping("/decoder-config")
    @PreAuthorize("hasRole('ADMIN')")
    public SetupProgressDto updateDecoderConfig(Authentication auth, @RequestBody @Valid DecoderConfigUpdateRequest req) {
        clubProfileService.updateDecoderConfig(CurrentOfficial.actor(auth),
                req.decoderHost(), req.decoderPort(), req.decoderProtocol());
        return SetupProgressDto.from(setupService.getProgress());
    }

    /**
     * Adds an official during setup: {@code POST /api/v1/setup/staff}, {@code ADMIN}. Answers 201 with no body.
     * Recorded in the officials audit log with the signed-in admin as the actor, and given the same checks as the
     * Officials screen. 400 for a bad body or a role name that does not exist.
     */
    @Audited("official_audit_log")
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public void createStaff(Authentication auth, @RequestBody @Valid SetupStaffRequest req) {
        Set<Role> roles = req.roles().stream()
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        userService.createStaff(req.email(), req.password(), req.firstName(), req.lastName(), roles,
                CurrentOfficial.id(auth));
    }
}
