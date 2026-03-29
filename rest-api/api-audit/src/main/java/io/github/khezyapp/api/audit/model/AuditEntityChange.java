package io.github.khezyapp.api.audit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * Data Transfer Object (DTO) representing a set of state changes for a specific domain entity.
 * <p>
 * This class captures the "what, where, and how" of a data mutation, linking the
 * changes to a specific entity instance and a global trace identifier for
 * cross-system observability.
 * </p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEntityChange {
    /**
     * The actual Java class of the entity being audited.
     * <p>
     * This field is ignored during JSON serialization to keep the log output
     * clean and focused on the entity name.
     * </p>
     */
    @JsonIgnore
    private Class<?> entityClass;

    /**
     * The unique identifier for the distributed trace, typically sourced from the MDC.
     * <p>
     * Used to correlate data-level changes with the high-level request that triggered them.
     * </p>
     */
    private String traceId;

    /**
     * The simple name or a descriptive alias for the entity (e.g., "User", "Order").
     */
    private String entityName;

    /**
     * The unique identifier of the specific entity instance being modified.
     */
    private Object entityId;

    /**
     * A detailed list of individual field-level modifications, including
     * the property path, the original value, and the new value.
     */
    private List<EntityFieldChange> changes;

}
