package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.audit.Actor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The official making the current request. The JWT subject is the official's user id
 * ({@link JwtTokenService#generateAccessToken}), so every endpoint that needs an official reads it here.
 *
 * <p>Only call this behind a rule that asks for an official role. With no signed-in official it throws an
 * {@link org.springframework.security.core.AuthenticationException}, which the API answers with a 401.
 */
public final class CurrentOfficial {

    private CurrentOfficial() {
    }

    /**
     * The id of the official behind the request's {@link Authentication}.
     *
     * @throws AuthenticationCredentialsNotFoundException if nobody is signed in
     * @throws BadCredentialsException                    if the subject is not a user id
     */
    public static long id(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new AuthenticationCredentialsNotFoundException("No official is signed in");
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("The token's subject is not an official", e);
        }
    }

    /** For code that is not handed the request's {@link Authentication}. */
    public static long id() {
        return id(SecurityContextHolder.getContext().getAuthentication());
    }

    /** The official as the actor to record in the audit log. */
    public static Actor actor(Authentication auth) {
        return Actor.official(id(auth));
    }

    /** As {@link #actor(Authentication)}, for code that is not handed the request's {@link Authentication}. */
    public static Actor actor() {
        return Actor.official(id());
    }
}
