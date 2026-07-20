package io.github.khezyapp.examples.security.mfa.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public AuthController(
            @Value("${khezy.api.security.jwt.secret}") final String secret,
            @Value("${khezy.api.security.jwt.expiration:3600}") final long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationSeconds * 1000;
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestBody final Map<String, Object> body) {
        final var username = (String) body.getOrDefault("username", "user");
        @SuppressWarnings("unchecked")
        final var factors = (List<String>) body.getOrDefault("factors", List.of("password"));

        final var roles = List.of("ROLE_USER");
        final var now = new Date();
        final var token = Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .claim("factors", factors)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();

        return ResponseEntity.ok(Map.of(
                "token", token,
                "factors", factors
        ));
    }
}
