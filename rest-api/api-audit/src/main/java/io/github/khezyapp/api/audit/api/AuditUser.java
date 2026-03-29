package io.github.khezyapp.api.audit.api;

import java.io.Serializable;

/**
 * Generic interface representing the identity and credentials of a user
 * captured within an audit event.
 * <p>
 * This interface allows the audit system to identify the actor responsible
 * for a change or request, supporting any serializable identifier type
 * (e.g., {@link Long}, {@link String}, or {@link java.util.UUID}).
 * </p>
 *
 * @param <T> the type of the user's unique identifier, which must be {@link Serializable}
 */
public interface AuditUser<T extends Serializable> {

    /**
     * Retrieves the unique identifier of the user.
     * * @return the user's ID
     */
    T getId();

    /**
     * Retrieves the human-readable identifier or login name of the user.
     * * @return the username
     */
    String getUsername();
}
