package io.github.khezyapp.examples.security.custom;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityCustomizationApplicationTests {

    @LocalServerPort
    private int port;

    private RestTestClient restTemplate;

    @Value("${khezy.api.security.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        restTemplate = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturnCustom401FormatWhenNoToken() {
        restTemplate.get()
                .uri("/secure")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsEntry("error", "unauthorized");
                    assertThat(body).containsEntry("message", "Custom message authentication required");
                });
    }

    @Test
    void shouldAcceptCustomXAuthTokenHeader() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("message", "Access granted to user"));
    }

    @Test
    void shouldFallbackToBearerWhenNoXAuthToken() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnCustom403FormatWhenMfaMissing() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsEntry("error", "access_denied");
                    assertThat(body).containsEntry("path", "/secure");
                    assertThat(body).containsKey("timestamp");
                });
    }

    @Test
    void shouldUseCustomMfaClaimName() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldNotUseDefaultFactorsClaim() {
        final var token = Jwts.builder()
                .subject("user")
                .claim("authorities", List.of("ROLE_USER"))
                .claim("factors", List.of("password", "webauthn"))
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(
                        jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullMfaFlowWithCustomComponents() {
        final var pwToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", pwToken)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("error", "access_denied"));

        final var mfaToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        restTemplate.get()
                .uri("/secure")
                .header("X-Auth-Token", mfaToken)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    private String generateToken(final String username,
                                  final List<String> roles,
                                  final List<String> mfaClaims,
                                  final String claimKey) {
        final SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
        final var now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .claim(claimKey, mfaClaims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }
}
