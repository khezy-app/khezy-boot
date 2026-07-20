package io.github.khezyapp.api.security.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT authentication.
 * Prefix: khezy.api.security.jwt
 * <p>
 * Setting {@code khezy.api.security.jwt.secret} enables the JWT filter.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "khezy.api.security.jwt")
public class KhezyJwtProperties {

    /**
     * HMAC signing secret for JWT validation.
     * When set, the JWT authentication filter is auto-configured.
     * When null/empty, JWT filter is disabled.
     */
    private String secret;

    /**
     * Expected issuer claim for JWT validation.
     * When set, tokens with a different issuer are rejected.
     */
    private String issuer;

    /**
     * Token expiration in seconds. Default: 3600 (1 hour).
     */
    private long expiration = 3600;

    /**
     * Claim key used to extract MFA factor authorities from the token.
     * Default: "factors".
     */
    private String factorsClaim = "factors";
}
