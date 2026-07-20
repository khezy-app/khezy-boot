package io.github.khezyapp.api.security.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.config.http.SessionCreationPolicy;

import java.util.List;

/**
 * Configuration properties for KHEZY API Security.
 * Prefix: khezy.api.security
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "khezy.api.security")
public class KhezySecurityProperties {

    /**
     * Enable/disable the default security filter chain.
     * When {@code false}, users must define their own {@code SecurityFilterChain} bean.
     */
    private boolean enabled = true;

    /**
     * Permit all requests without authentication.
     * Useful for development/testing environments.
     */
    private boolean permitAll = false;

    /**
     * URL patterns to permit without authentication.
     * Defaults to {@code /auth/**}.
     */
    private List<String> permitPatterns = List.of("/auth/**");

    /**
     * Session creation policy. Defaults to {@code STATELESS} for REST APIs.
     */
    private SessionCreationPolicy sessionCreationPolicy = SessionCreationPolicy.STATELESS;
}
