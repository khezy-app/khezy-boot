package io.github.khezyapp.examples.security.custom.auth;

import io.github.khezyapp.api.security.token.TokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CustomTokenExtractor implements TokenExtractor {

    @Override
    public String scheme() {
        return "X-Auth-Token";
    }

    @Override
    public Optional<String> extract(final HttpServletRequest request) {
        final var customHeader = request.getHeader("X-Auth-Token");
        if (customHeader != null && !customHeader.isBlank()) {
            return Optional.of(customHeader);
        }
        final var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Optional.of(authHeader.substring(7));
        }
        return Optional.empty();
    }
}
