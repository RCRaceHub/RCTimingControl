package dev.monkeypatch.rctiming.security;

import dev.monkeypatch.rctiming.domain.user.Role;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.persistence.DatabaseProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Officials' tokens. The access token is a short-lived JWT signed with HMAC-SHA256 and carries the official's
 * id, name and roles, so a request needs no database read to be authorised. The refresh token is not a JWT: it
 * is an opaque random value, and the database keeps only its SHA-256 hash ({@code AuthService}), so it can be
 * revoked and a stolen database copy cannot be used to sign in.
 *
 * <p>The signing key comes from {@code app.jwt.secret} or, when that is unset, from a key file the app creates
 * in its data folder on first start ({@link JwtSecretFile}).
 */
@Service
public class JwtTokenService {

    private final io.jsonwebtoken.security.MacAlgorithm signingAlg;
    private final javax.crypto.SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;

    public JwtTokenService(
            @Value("${app.jwt.secret:}") String base64Secret,
            @Value("${app.jwt.access-token-ttl-ms}") long accessTokenTtlMs,
            @Value("${app.jwt.refresh-token-ttl-ms}") long refreshTokenTtlMs,
            DatabaseProperties database) {
        if (base64Secret == null || base64Secret.isBlank()) {
            base64Secret = JwtSecretFile.loadOrCreate(database.effectiveDataDirectory());
        }
        this.signingAlg = Jwts.SIG.HS256;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenTtlMs))
                .signWith(signingKey, signingAlg)
                .compact();
    }

    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * The Spring Security authorities for a parsed token's {@code roles} claim, one {@code ROLE_} per role.
     * A token without the claim gets none, so every rule that asks for an official role refuses it.
     */
    public static List<GrantedAuthority> authorities(Claims claims) {
        List<?> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    public long getRefreshTokenTtlMs() {
        return refreshTokenTtlMs;
    }
}
