package io.github.khezyapp.examples.security.mfa;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityMfaApplicationTests {

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
    void shouldReturn401WhenNoToken() {
        restTemplate.get()
                .uri("/secure")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn403WhenMissingRequiredFactor() {
        final var token = generateToken("user", List.of("ROLE_USER"), List.of("password"));
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsEntry("requiredMFA", true);
                    assertThat(body).containsEntry("mfaMethod", "webauthn");
                });
    }

    @Test
    void shouldReturn200WhenAllFactorsPresent() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"));
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("message", "Access granted to user"));
    }

    @Test
    void shouldReturn403WhenOnlyWebauthnFactorMissing() {
        final var token = generateToken("user", List.of("ROLE_USER"), List.of("webauthn"));
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsEntry("requiredMFA", true);
                    assertThat(body).containsEntry("mfaMethod", "password");
                });
    }

    @Test
    void fullFlowPasswordThenMfaThenAccess() {
        final var pwToken = generateToken("user", List.of("ROLE_USER"), List.of("password"));
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + pwToken)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("requiredMFA", true));

        final var mfaToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"));
        restTemplate.get()
                .uri("/secure")
                .header("Authorization", "Bearer " + mfaToken)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldGenerateTokenFromAuthEndpoint() {
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.<String, Object>of(
                        "username", "user",
                        "factors", List.of("password", "webauthn")
                ))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> {
                    assertThat(body).containsKey("token");
                    assertThat(body).containsKey("factors");
                });
    }

    private String generateToken(final String username,
                                  final List<String> roles,
                                  final List<String> factors) {
        final SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
        final var now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .claim("factors", factors)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }
}
