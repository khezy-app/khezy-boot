package io.github.khezyapp.examples.security.mfa;

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
class SecurityMfaApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${khezy.api.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturn401WhenNoToken() {
        final var response = restTemplate.getForEntity("/secure", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn403WhenMissingRequiredFactor() {
        final var token = generateToken("user", List.of("ROLE_USER"), List.of("password"));
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("requiredMFA", true);
        assertThat(response.getBody()).containsEntry("mfaMethod", "webauthn");
    }

    @Test
    void shouldReturn200WhenAllFactorsPresent() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"));
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Access granted to user");
    }

    @Test
    void shouldReturn403WhenOnlyWebauthnFactorMissing() {
        final var token = generateToken("user", List.of("ROLE_USER"), List.of("webauthn"));
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("requiredMFA", true);
        assertThat(response.getBody()).containsEntry("mfaMethod", "password");
    }

    @Test
    void fullFlowPasswordThenMfaThenAccess() {
        final var pwToken = generateToken("user", List.of("ROLE_USER"), List.of("password"));
        final var pwHeaders = new HttpHeaders();
        pwHeaders.setBearerAuth(pwToken);
        final var pwResponse = restTemplate.exchange(
                "/secure", HttpMethod.GET, new HttpEntity<>(pwHeaders), Map.class);
        assertThat(pwResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pwResponse.getBody()).containsEntry("requiredMFA", true);

        final var mfaToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"));
        final var mfaHeaders = new HttpHeaders();
        mfaHeaders.setBearerAuth(mfaToken);
        final var mfaResponse = restTemplate.exchange(
                "/secure", HttpMethod.GET, new HttpEntity<>(mfaHeaders), Map.class);
        assertThat(mfaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldGenerateTokenFromAuthEndpoint() {
        final var body = Map.<String, Object>of(
                "username", "user",
                "factors", List.of("password", "webauthn")
        );
        final var request = new HttpEntity<>(body);
        final var response = restTemplate.postForEntity(
                "/auth/token", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody()).containsKey("factors");
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
