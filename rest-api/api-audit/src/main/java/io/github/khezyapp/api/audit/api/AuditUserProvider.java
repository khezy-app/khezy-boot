package io.github.khezyapp.api.audit.api;

import java.io.Serializable;

/**
 * Strategy interface for resolving the current user context during an audit event.
 * <p>
 * Implementations are responsible for extracting the authenticated principal's
 * identity and metadata from the current security context or request state.
 * </p>
 */
public interface AuditUserProvider {

    /**
     * Retrieves the identity of the user performing the current operation.
     *
     * @return an {@link AuditUser} containing the serializable identifier and
     * attributes of the current actor, or a guest/system fallback if
     * no authentication is present.
     */
    AuditUser<? extends Serializable> getCurrentUser();

}
