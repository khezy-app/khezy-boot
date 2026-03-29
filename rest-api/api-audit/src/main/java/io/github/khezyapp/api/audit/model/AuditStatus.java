package io.github.khezyapp.api.audit.model;

/**
 * Represents the final execution status of an audited operation.
 * <p>
 * This enumeration is used to categorize whether the intercepted method
 * completed normally or encountered an exception during its lifecycle.
 * </p>
 */
public enum AuditStatus {
    /**
     * Indicates that the operation was completed successfully without throwing any exceptions.
     */
    SUCCESS,

    /**
     * Indicates that the operation failed, typically due to an unhandled exception
     * or a business logic error that prevented completion.
     */
    FAILURE
}
