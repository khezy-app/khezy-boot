package io.github.khezyapp.examples.security.rowlevel;

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
class RowLevelApplicationTests {

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
                .uri("/invoices")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldSeeOnlyOwnTenantInvoicesWhenListing() {
        final var token = generateToken("alice");
        restTemplate.get()
                .uri("/invoices")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(List.class)
                .value(body -> assertThat(body).hasSize(3));
    }

    @Test
    void shouldSeeZeroInvoicesForUnknownTenant() {
        final var token = generateToken("alice");
        restTemplate.get()
                .uri("/invoices")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "nonexistent")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(List.class)
                .value(body -> assertThat(body).hasSize(0));
    }

    @Test
    void shouldAccessOwnTenantInvoiceById() {
        final var token = generateToken("alice");
        restTemplate.get()
                .uri("/invoices/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("tenantId", "acme"));
    }

    @Test
    void shouldNotSeeOtherTenantInvoiceById() {
        final var token = generateToken("alice");
        restTemplate.get()
                .uri("/invoices/4")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSeeCorrectSummaryForOwnTenant() {
        final var token = generateToken("alice");
        restTemplate.get()
                .uri("/invoices/summary")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "acme")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.OK)
                .expectBody(Map.class)
                .value(body -> assertThat(body).containsEntry("totalAmount", 4250.0));
    }

    private String generateToken(final String username) {
        final SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
        final var now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("authorities", List.of("ROLE_USER"))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }
}
