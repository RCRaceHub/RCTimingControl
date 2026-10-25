package dev.monkeypatch.rctiming.api.auth;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.security.AuthService;
import dev.monkeypatch.rctiming.security.AuthService.Outcome;
import dev.monkeypatch.rctiming.security.AuthService.Refusal;
import dev.monkeypatch.rctiming.security.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Officials' sign-in, refresh and sign-out under {@code /api/v1/auth}; {@link AuthService} does the work and this
 * sets the cookie. All three routes are open to anyone ({@code SecurityConfig}) because they are how a caller gets
 * a token; the answer depends on the credentials or cookie sent. Racers and spectators have no account here.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthService authService, JwtTokenService jwtTokenService) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Signs in with email and password: {@code POST /api/v1/auth/login}, open to anyone. Every attempt is recorded
     * in the audit log, wrong passwords included.
     *
     * <p>On success answers 200 with an {@link AuthResponse} and sets the refresh cookie. A wrong email or password
     * is a bare 401 that does not say which was wrong. An account that is not an official is a bare 403. A
     * disabled account is a 403 {@link ProblemDetail} with {@code reason: "disabled"}, since the password was right.
     * A malformed body is a 400.
     */
    @Audited("audit_log")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request, HttpServletResponse response) {
        return switch (authService.login(request.email(), request.password())) {
            case Outcome.SignedIn signedIn -> signedIn(signedIn, response);
            case Outcome.Refused(Refusal refusal) -> switch (refusal) {
                // Generic answer: never say which field is wrong
                case WRONG_CREDENTIALS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                // The password was right, so say why
                case DISABLED -> {
                    ProblemDetail disabled = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                            "This account has been disabled. Ask a club admin to enable it.");
                    disabled.setProperty("reason", "disabled");
                    yield ResponseEntity.status(HttpStatus.FORBIDDEN).body(disabled);
                }
                case NOT_AN_OFFICIAL -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                case UNKNOWN_TOKEN, TOKEN_USED, TOKEN_EXPIRED, NO_SUCH_OFFICIAL ->
                        throw new IllegalStateException("Not a sign-in refusal: " + refusal);
            };
        };
    }

    /**
     * Swaps the refresh cookie for a new access token: {@code POST /api/v1/auth/refresh}, open to anyone who can
     * send the cookie; see {@link AuthService#refresh}. No cookie at all is the normal answer to an anonymous
     * visitor and is not recorded.
     *
     * <p>On success answers 200 with an {@link AuthResponse} and sets a new refresh cookie. An unknown, used or
     * expired token, an official who no longer exists and a disabled account all answer a bare 401; an account
     * that is no longer an official answers a bare 403.
     */
    @Audited("audit_log")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String rawCookieToken,
            HttpServletResponse response) {
        if (rawCookieToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return switch (authService.refresh(rawCookieToken)) {
            case Outcome.SignedIn signedIn -> signedIn(signedIn, response);
            case Outcome.Refused(Refusal refusal) -> switch (refusal) {
                case NOT_AN_OFFICIAL -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                // A disabled account answers 401 like a dead token, so the page goes back to sign-in
                case UNKNOWN_TOKEN, TOKEN_USED, TOKEN_EXPIRED, NO_SUCH_OFFICIAL, DISABLED, WRONG_CREDENTIALS ->
                        ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            };
        };
    }

    /**
     * Signs this browser out: revokes the refresh token its cookie holds and expires the cookie. Without
     * it, clearing the page's in-memory access token would not sign anyone out, because the 7-day cookie
     * would simply issue a new one on the next load.
     *
     * <p>This is {@code DELETE /refresh} rather than a separate {@code /logout} URL because the cookie's
     * path is {@code /api/v1/auth/refresh}: the browser sends it only to that path. Open to anyone who can
     * send the cookie (the access token has usually expired by then) and idempotent: no cookie, an unknown
     * token or an already revoked one all answer 204. Only this browser's sign-in is ended, so the same
     * official stays signed in elsewhere. The access token already issued stays valid until it expires
     * (15 minutes).
     *
     * <p>Revokes the token's whole family; see {@link AuthService#logout}. The page also waits for its own
     * in-flight refresh before calling this (see {@code endSession} in the frontend's {@code lib/auth.ts}).
     */
    @Audited("audit_log")
    @DeleteMapping("/refresh")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String rawCookieToken,
            HttpServletResponse response) {
        if (rawCookieToken != null) {
            authService.logout(rawCookieToken);
        }
        // Same name, path and attributes as the cookie that was set, with no lifetime, so the browser drops it
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> signedIn(Outcome.SignedIn signedIn, HttpServletResponse response) {
        setRefreshCookie(signedIn.refreshToken(), response);
        return ResponseEntity.ok(AuthResponse.of(signedIn.user(), signedIn.accessToken()));
    }

    private void setRefreshCookie(String rawToken, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(rawToken, Duration.ofMillis(jwtTokenService.getRefreshTokenTtlMs())).toString());
    }

    /**
     * The refresh cookie. Not marked Secure because RCTC is served over plain HTTP on the venue network
     * (HTTPS is optional and runs beside it, #503, so one cookie setting must suit both); SameSite=Lax keeps it off cross-site POSTs, and the path keeps it off every other request.
     */
    private static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth/refresh")
                .maxAge(maxAge)
                .build();
    }
}
