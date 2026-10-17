package dev.monkeypatch.rctiming.api.auth;

import dev.monkeypatch.rctiming.domain.user.User;

import java.util.List;

/**
 * The answer to a successful sign-in or refresh ({@link AuthController}).
 *
 * @param accessToken the JWT to send as {@code Authorization: Bearer}; it expires after about 15 minutes
 * @param id          the official's user id, as text
 * @param roles       the official's role names ({@code ADMIN}, {@code RACE_DIRECTOR}, {@code REFEREE}); a user can hold
 *                    several
 */
public record AuthResponse(
        String accessToken,
        String id,
        String email,
        String firstName,
        String lastName,
        List<String> roles
) {

    /** The signed-in official and the access token to send with each request. */
    public static AuthResponse of(User user, String accessToken) {
        return new AuthResponse(
                accessToken,
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream().map(Enum::name).toList()
        );
    }
}
