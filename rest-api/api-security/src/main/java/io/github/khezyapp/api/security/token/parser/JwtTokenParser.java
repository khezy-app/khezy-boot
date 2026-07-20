package io.github.khezyapp.api.security.token.parser;

import io.github.khezyapp.api.security.token.TokenParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link TokenParser} implementation using jjwt for JWT parsing.
 * Configured with a signing secret and optional issuer validation.
 */
public class JwtTokenParser implements TokenParser {

    private final SecretKey key;
    private final String issuer;

    /**
     * Creates a JWT token parser with the given signing secret.
     *
     * @param secret the HMAC signing secret (minimum 256 bits)
     */
    public JwtTokenParser(final String secret) {
        this(secret, null);
    }

    /**
     * Creates a JWT token parser with the given signing secret and issuer.
     *
     * @param secret the HMAC signing secret (minimum 256 bits)
     * @param issuer the expected issuer claim, may be null to skip issuer validation
     */
    public JwtTokenParser(final String secret,
                          final String issuer) {
        this.key = Keys.hmacShaKeyFor(
                Objects.requireNonNull(secret, "secret must not be null").getBytes(StandardCharsets.UTF_8)
        );
        this.issuer = issuer;
    }

    @Override
    public ParsedToken parse(final String token) {
        final var builder = Jwts.parser()
                .verifyWith(key);
        if (Objects.nonNull(issuer)) {
            builder.requireIssuer(issuer);
        }
        final Claims claims;
        try {
            claims = builder.build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (final ExpiredJwtException e) {
            throw new TokenException("Token has expired", e);
        } catch (final Exception e) {
            throw new TokenException("Invalid token", e);
        }
        return new ParsedToken(
                claims.getSubject(),
                claims,
                extractAuthorities(claims)
        );
    }

    private List<String> extractAuthorities(final Claims claims) {
        final var realmAccess = claims.get("realm_access", Map.class);
        if (Objects.nonNull(realmAccess)) {
            final var roles = realmAccess.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toList();
            }
        }
        final var authorities = claims.get("authorities");
        if (authorities instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }
}
