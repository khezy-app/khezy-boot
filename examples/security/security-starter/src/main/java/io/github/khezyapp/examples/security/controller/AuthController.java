package io.github.khezyapp.examples.security.controller;

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
class AuthController {

    private final SecretKey signingKey;
    private final long expirationMillis;

    AuthController(
            @Value("${khezy.api.security.jwt.secret}") final String secret,
            @Value("${khezy.api.security.jwt.expiration:3600}") final long expirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationSeconds * 1000;
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> token(@RequestBody final Map<String, String> body) {
        final var username = body.getOrDefault("username", "user");
        final var password = body.getOrDefault("password", "user");

        if (!"user".equals(password) && !"admin".equals(password)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid credentials"));
        }

        final var roles = "admin".equals(username)
                ? List.of("ROLE_USER", "ROLE_ADMIN")
                : List.of("ROLE_USER");

        final var now = new Date();
        final var token = Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(signingKey)
                .compact();

        return ResponseEntity.ok(Map.of("token", token));
    }
}
