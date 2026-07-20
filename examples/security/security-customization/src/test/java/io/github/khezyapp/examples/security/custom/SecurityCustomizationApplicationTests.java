package io.github.khezyapp.examples.security.custom;

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
class SecurityCustomizationApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${khezy.api.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturnCustom401FormatWhenNoToken() {
        final var response = restTemplate.getForEntity("/secure", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "unauthorized");
        assertThat(response.getBody()).containsEntry("message", "Custom message authentication required");
    }

    @Test
    void shouldAcceptCustomXAuthTokenHeader() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        final var headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Access granted to user");
    }

    @Test
    void shouldFallbackToBearerWhenNoXAuthToken() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnCustom403FormatWhenMfaMissing() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password"), "mfa_claims");
        final var headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "access_denied");
        assertThat(response.getBody()).containsEntry("path", "/secure");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void shouldUseCustomMfaClaimName() {
        final var token = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        final var headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        final var headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange(
                "/secure", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fullMfaFlowWithCustomComponents() {
        final var pwToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password"), "mfa_claims");
        final var pwHeaders = new HttpHeaders();
        pwHeaders.set("X-Auth-Token", pwToken);
        final var pwResponse = restTemplate.exchange(
                "/secure", HttpMethod.GET, new HttpEntity<>(pwHeaders), Map.class);
        assertThat(pwResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pwResponse.getBody()).containsEntry("error", "access_denied");

        final var mfaToken = generateToken("user", List.of("ROLE_USER"),
                List.of("password", "webauthn"), "mfa_claims");
        final var mfaHeaders = new HttpHeaders();
        mfaHeaders.set("X-Auth-Token", mfaToken);
        final var mfaResponse = restTemplate.exchange(
                "/secure", HttpMethod.GET, new HttpEntity<>(mfaHeaders), Map.class);
        assertThat(mfaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
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
