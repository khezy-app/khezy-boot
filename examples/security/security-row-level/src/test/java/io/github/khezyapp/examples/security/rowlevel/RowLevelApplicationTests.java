package io.github.khezyapp.examples.security.rowlevel;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RowLevelApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${khezy.api.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturn401WhenNoToken() {
        final var response = restTemplate.getForEntity("/invoices", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldSeeOnlyOwnTenantInvoicesWhenListing() {
        final var token = generateToken("alice");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Tenant-Id", "acme");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/invoices", HttpMethod.GET, request, List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void shouldSeeZeroInvoicesForUnknownTenant() {
        final var token = generateToken("alice");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Tenant-Id", "nonexistent");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/invoices", HttpMethod.GET, request, List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(0);
    }

    @Test
    void shouldAccessOwnTenantInvoiceById() {
        final var token = generateToken("alice");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Tenant-Id", "acme");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/invoices/1", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("tenantId", "acme");
    }

    @Test
    void shouldNotSeeOtherTenantInvoiceById() {
        final var token = generateToken("alice");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Tenant-Id", "acme");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/invoices/4", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSeeCorrectSummaryForOwnTenant() {
        final var token = generateToken("alice");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Tenant-Id", "acme");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/invoices/summary", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("totalAmount", 4250.0);
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
