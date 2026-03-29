package io.github.khezyapp.api.audit;

import io.github.khezyapp.api.audit.api.AuditLogService;
import io.github.khezyapp.api.audit.model.AuditEntityChange;
import io.github.khezyapp.api.audit.model.AuditLogRecord;
import lombok.extern.slf4j.Slf4j;

/**
 * A default, "no-op" implementation of the {@link AuditLogService}.
 * <p>
 * This class serves as a fallback implementation when no custom {@code AuditLogService}
 * is provided in the Spring application context. Instead of persisting logs to a
 * database or message broker, it simply outputs the audit data to the system log
 * at the {@code DEBUG} level.
 * </p>
 * <p>
 * This is useful for local development, debugging, or as a safety mechanism to
 * prevent application startup failure if auditing is enabled but no storage
 * destination is configured.
 * </p>
 */
@Slf4j
public class NoopAuditLogService implements AuditLogService {

    /**
     * Logs the high-level request audit record to the console or log file.
     * <p>
     * Output is visible only if the logging level for this class is set to {@code DEBUG}.
     * </p>
     *
     * @param auditLogRecord the structured record containing request and execution metadata
     */
    @Override
    public void onRequest(final AuditLogRecord auditLogRecord) {
        log.debug("AuditLogRecord: {}", auditLogRecord);
    }

    /**
     * Logs fine-grained entity state changes to the console or log file.
     * <p>
     * Output is visible only if the logging level for this class is set to {@code DEBUG}.
     * </p>
     *
     * @param auditEntityChange the object containing the diff of entity field changes
     */
    @Override
    public void onAuditEntityChanges(final AuditEntityChange auditEntityChange) {
        log.debug("AuditEntityChange: {}", auditEntityChange);
    }
}
