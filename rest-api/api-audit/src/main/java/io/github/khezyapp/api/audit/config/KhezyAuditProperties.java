package io.github.khezyapp.api.audit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Khezy Audit library.
 * <p>
 * These properties are bound from the application configuration (e.g., {@code application.yml}
 * or {@code application.properties}) using the prefix {@code khezy.audit}.
 * </p>
 */
@ConfigurationProperties(prefix = "khezy.audit")
@Getter
@Setter
public class KhezyAuditProperties {
    /**
     * Globally enables or disables the auditing of high-level web requests and method invocations.
     * <p>
     * When set to {@code true}, methods annotated with {@code @AuditLog} will be
     * intercepted and their metadata recorded. Defaults to {@code false}.
     * </p>
     */
    private boolean enabledAuditRequest = false;

    /**
     * Globally enables or disables the auditing of fine-grained entity state changes.
     * <p>
     * When set to {@code true}, database mutations (create, update, delete) captured
     * by the Hibernate interceptor will be processed and logged. Defaults to {@code false}.
     * </p>
     */
    private boolean enabledAuditEntityChanges = false;
}
