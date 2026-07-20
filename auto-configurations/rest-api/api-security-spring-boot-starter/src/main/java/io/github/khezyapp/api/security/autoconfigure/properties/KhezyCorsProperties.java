package io.github.khezyapp.api.security.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for Cross-Origin Resource Sharing (CORS).
 * Prefix: khezy.api.cors
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "khezy.api.cors")
public class KhezyCorsProperties {
    /**
     * Whether the browser should include credentials (cookies, auth headers) in the request.
     */
    private boolean allowCredentials = false;

    /**
     * List of headers allowed in actual requests. Use "*" to allow all.
     */
    private List<String> allowedHeaders = List.of("*");

    /**
     * List of origins allowed to access the resource. Use "*" to allow all (not recommended with credentials).
     */
    private List<String> allowedOrigins;

    /**
     * List of HTTP methods allowed (GET, POST, etc.). Use "*" to allow all.
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    /**
     * List of response headers that the browser is allowed to access.
     */
    private List<String> exposedHeaders;

    /**
     * Whether to allow requests from a private network (e.g. localhost to a public IP).
     */
    private Boolean allowPrivateNetwork = false;

    /**
     * Patterns used to match origins (e.g. "https://*.domain.com"). Useful for dynamic subdomains.
     */
    private List<String> allowedOriginPatterns;

    /**
     * How long (in seconds) the response to a preflight request can be cached by the browser.
     */
    private Long maxAge;

    /**
     * The URL path patterns where this CORS configuration should be applied.
     */
    private String pathPattern;
}
