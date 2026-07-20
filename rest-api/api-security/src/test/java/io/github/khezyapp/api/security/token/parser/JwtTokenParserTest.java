package io.github.khezyapp.api.security.token.parser;

import io.github.khezyapp.api.security.token.TokenParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenParserTest {

    private static final String SECRET = "my-test-secret-key-that-is-at-least-256-bits-long!";
    private static final String ISSUER = "test-issuer";

    private JwtTokenParser parser;

    @BeforeEach
    void setUp() {
        parser = new JwtTokenParser(SECRET, ISSUER);
    }

    @Test
    void shouldParseValidToken() {
        final var token = createToken(Map.of(
                Claims.SUBJECT, "user123",
                "authorities", List.of("ROLE_USER"),
                "factors", List.of("PASSWORD")
        ), 3600);

        final var result = parser.parse(token);

        assertThat(result.subject()).isEqualTo("user123");
        assertThat(result.grantedAuthorities()).contains("ROLE_USER");
        assertThat(result.claims()).containsKey("factors");
    }

    @Test
    void shouldExtractAuthoritiesFromRealmAccess() {
        final var token = createToken(Map.of(
                Claims.SUBJECT, "user123",
                "realm_access", Map.of("roles", List.of("admin", "user"))
        ), 3600);

        final var result = parser.parse(token);

        assertThat(result.grantedAuthorities()).contains("admin", "user");
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNoAuthClaim() {
        final var token = createToken(Map.of(Claims.SUBJECT, "user123"), 3600);

        final var result = parser.parse(token);

        assertThat(result.grantedAuthorities()).isEmpty();
    }

    @Test
    void shouldRejectExpiredToken() {
        final var token = createToken(Map.of(Claims.SUBJECT, "user123"), -1);

        assertThatThrownBy(() -> parser.parse(token))
                .isInstanceOf(TokenParser.TokenException.class)
                .hasCauseInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void shouldRejectInvalidSignature() {
        final var otherKey = Keys.hmacShaKeyFor("a-different-secret-key-that-is-also-256-bits-long!".getBytes());
        final var token = Jwts.builder()
                .subject("user123")
                .signWith(otherKey)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .compact();

        assertThatThrownBy(() -> parser.parse(token))
                .isInstanceOf(TokenParser.TokenException.class);
    }

    @Test
    void shouldAllowNullSubject() {
        final var parserNoIssuer = new JwtTokenParser(SECRET);
        final var key = Keys.hmacShaKeyFor(SECRET.getBytes());
        final var token = Jwts.builder()
                .signWith(key)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .compact();

        final var result = parserNoIssuer.parse(token);

        assertThat(result.subject()).isNull();
        assertThat(result.grantedAuthorities()).isEmpty();
    }

    @Test
    void shouldRejectInvalidIssuer() {
        final var parserWithIssuer = new JwtTokenParser(SECRET, "wrong-issuer");
        final var token = createToken(Map.of(
                Claims.SUBJECT, "user123",
                Claims.ISSUER, "test-issuer"
        ), 3600);

        assertThatThrownBy(() -> parserWithIssuer.parse(token))
                .isInstanceOf(TokenParser.TokenException.class);
    }

    @Test
    void shouldThrowForNullSecret() {
        assertThatThrownBy(() -> new JwtTokenParser(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAcceptTokenWithoutIssuerValidation() {
        final var parserNoIssuer = new JwtTokenParser(SECRET);
        final var token = createToken(Map.of(Claims.SUBJECT, "user123"), 3600);

        final var result = parserNoIssuer.parse(token);

        assertThat(result.subject()).isEqualTo("user123");
    }

    private String createToken(final Map<String, Object> claims,
                               final long expirationSeconds) {
        final var key = Keys.hmacShaKeyFor(SECRET.getBytes());
        final var builder = Jwts.builder()
                .signWith(key)
                .claims(claims)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS)));
        if (!claims.containsKey(Claims.ISSUER) && ISSUER != null) {
            builder.issuer(ISSUER);
        }
        return builder.compact();
    }
}
