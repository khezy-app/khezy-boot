package io.github.khezyapp.examples.security;

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
class SecurityStarterApplicationTests {

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
                .uri("/")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenInvalidToken() {
        restTemplate.get()
                .uri("/")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn200WhenValidToken() {
        final var token = generateToken("user", List.of("ROLE_USER"));
        restTemplate.get()
                .uri("/")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("user"));
    }

    @Test
    void shouldReturnTokenFromAuthEndpoint() {
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "user", "password", "user"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsKey("token"));
    }

    @Test
    void shouldReturn400WhenInvalidCredentials() {
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "user", "password", "wrong"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldAllowAuthEndpointWithoutToken() {
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void fullFlowAuthenticateThenAccessProtectedEndpoint() {
        final String[] token = new String[1];
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "user", "password", "user"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> token[0] = (String) body.get("token"));

        restTemplate.get()
                .uri("/")
                .header("Authorization", "Bearer " + token[0])
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("user"));
    }

    private String generateToken(final String username,
                                 final List<String> roles) {
        final SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        final var now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }
}
