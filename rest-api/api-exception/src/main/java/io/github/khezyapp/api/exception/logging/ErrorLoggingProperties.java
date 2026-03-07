package io.github.khezyapp.api.exception.logging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for fine-tuning the error logging behavior of the library.
 * <p>
 * These properties are bound to the prefix {@code khezy.error-handling.logging} and allow
 * developers to toggle logging, set severity thresholds, and control stack trace verbosity.
 * </p>
 */
@ConfigurationProperties(prefix = "khezy.error-handling.logging")
@Getter
@Setter
public class ErrorLoggingProperties {
    /** * Global toggle to enable or disable automatic logging in the library's handlers.
     * Defaults to {@code true}.
     */
    private boolean enabled = true;

    /** * The minimum HTTP status code (e.g., 500) that triggers an ERROR level log.
     * Status codes below this threshold are typically logged at the WARN level.
     */
    private int errorThreshold = 500;

    /** * Whether to include the full exception stack trace in the log output.
     * Disabling this can reduce log volume in high-traffic environments.
     */
    private boolean includeStackTrace = true;
}
