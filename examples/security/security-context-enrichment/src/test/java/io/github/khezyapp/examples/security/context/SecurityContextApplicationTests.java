package io.github.khezyapp.examples.security.context;

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
class SecurityContextApplicationTests {

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
                .uri("/documents/1")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn200WhenOwnerAccessesOwnDocument() {
        final var token = generateToken("alice", List.of("ROLE_USER"));
        restTemplate.get()
                .uri("/documents/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Design Doc"));
    }

    @Test
    void shouldReturn403WhenNonOwnerAccessesDocument() {
        final var token = generateToken("bob", List.of("ROLE_USER"));
        restTemplate.get()
                .uri("/documents/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn403WhenCrossTenantAccess() {
        final var token = generateToken("alice", List.of("ROLE_USER"));
        restTemplate.get()
                .uri("/documents/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "globex")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn200WhenTenantMemberListsDocuments() {
        final var token = generateToken("alice", List.of("ROLE_USER"));
        restTemplate.get()
                .uri("/documents")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void fullFlowAuthenticateThenAccessDocument() {
        final String[] token = new String[1];
        restTemplate.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "alice", "password", "alice"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> token[0] = (String) body.get("token"));

        restTemplate.get()
                .uri("/documents/1")
                .header("Authorization", "Bearer " + token[0])
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    private String generateToken(final String username, final List<String> roles) {
        final SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
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
