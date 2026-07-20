package io.github.khezyapp.examples.security;

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
class SecurityStarterApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${khezy.api.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldReturn401WhenNoToken() {
        final var response = restTemplate.getForEntity("/", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenInvalidToken() {
        final var headers = new HttpHeaders();
        headers.setBearerAuth("invalid-token");
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange("/", HttpMethod.GET, request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn200WhenValidToken() {
        final var token = generateToken("user", List.of("ROLE_USER"));
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var request = new HttpEntity<>(headers);
        final var response = restTemplate.exchange("/", HttpMethod.GET, request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("user");
    }

    @Test
    void shouldReturnTokenFromAuthEndpoint() {
        final var body = Map.of("username", "user", "password", "user");
        final var request = new HttpEntity<>(body);
        final var response = restTemplate.postForEntity("/auth/token", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
    }

    @Test
    void shouldReturn400WhenInvalidCredentials() {
        final var body = Map.of("username", "user", "password", "wrong");
        final var request = new HttpEntity<>(body);
        final var response = restTemplate.postForEntity("/auth/token", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldAllowAuthEndpointWithoutToken() {
        final var body = Map.of("username", "admin", "password", "admin");
        final var request = new HttpEntity<>(body);
        final var response = restTemplate.postForEntity("/auth/token", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void fullFlowAuthenticateThenAccessProtectedEndpoint() {
        final var body = Map.of("username", "user", "password", "user");
        final var request = new HttpEntity<>(body);
        final var tokenResponse = restTemplate.postForEntity("/auth/token", request, Map.class);
        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        final var token = (String) tokenResponse.getBody().get("token");
        final var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        final var protectedRequest = new HttpEntity<>(headers);
        final var response = restTemplate.exchange("/", HttpMethod.GET, protectedRequest, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("user");
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
