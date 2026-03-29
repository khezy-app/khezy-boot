package io.github.khezyapp.api.audit.model;

import lombok.*;

import java.util.Objects;

/**
 * Data Transfer Object (DTO) representing a single field-level change
 * within an audited entity.
 * <p>
 * This class captures the property path, the previous and current values,
 * and the specific type of modification that occurred.
 * </p>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EntityFieldChange {
    /** The dot-separated path to the property (e.g., "user.address.street"). */
    private String property;

    /** The value before the change was applied. */
    private Object from;

    /** The value after the change was applied. */
    private Object to;

    /** The classification of the change (Added, Removed, or Updated). */
    private ChangeType changeType;

    public String getSummary() {
        if (Objects.isNull(changeType)) {
            return String.format("Property '%s' was modified.", property);
        }

        return switch (changeType) {
            case ADDED -> String.format("Added '%s' with value: [%s]", property, to);
            case REMOVED -> String.format("Removed '%s' (previous value: [%s])", property, from);
            case VALUE_CHANGES -> String.format("Updated '%s' from [%s] to [%s]", property, from, to);
        };
    }
}
