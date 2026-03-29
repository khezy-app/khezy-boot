package io.github.khezyapp.api.audit.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.khezyapp.api.audit.api.AuditUser;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a comprehensive snapshot of a business action, its execution context,
 * and its performance metrics within the auditing system.
 * <p>The record captures the full lifecycle of an intercepted method, including
 * distributed tracing identifiers, resource metadata, and execution duration.</p>
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogRecord {
    /** The unique identifier for the distributed trace. */
    private String traceId;

    /** The identifier for the specific unit of work within a trace. */
    private String spanId;

    /** The business-level operation name (e.g., "UPDATE_USER"). */
    private String action;

    /** The name or type of the primary resource affected. */
    private String entity;

    /** The unique identifier of the specific resource instance. */
    private Object entityId;

    /** The precise moment the audit record was instantiated after request completion. */
    private Instant timestamp;

    /** The execution latency of the intercepted method in milliseconds. */
    private Long duration;

    /** Encapsulates the identity of the actor who initiated the request. */
    private AuditUser<? extends Serializable> user;

    /** A flexible container for additional request/response context. */
    private AuditMetadata metadata;

    /** Represents the outcome of the operation (e.g., SUCCESS, FAILURE). */
    private AuditStatus status;

    /**
     * Captures the exception details if the intercepted method failed.
     * <p>
     * If the operation was successful, this field will be {@code null}.
     * When populated, it is serialized via a custom serializer to provide
     * a flattened view of the error type and message, excluding the stack trace
     * to maintain log brevity.
     * </p>
     */
    private Throwable error;
}
