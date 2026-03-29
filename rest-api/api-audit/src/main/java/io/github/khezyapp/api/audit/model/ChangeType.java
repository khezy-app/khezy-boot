package io.github.khezyapp.api.audit.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Enumeration defining the possible types of changes detected during the audit process.
 * <p>
 * Each type is associated with a standard symbol representation for serialized output,
 * such as JSON or logs.
 * </p>
 */
@Getter
public enum ChangeType {
    /** Indicates a new property or element was added (+). */
    ADDED("+"),

    /** Indicates an existing property or element was removed (-). */
    REMOVED("-"),

    /** Indicates the value of an existing property was modified (~). */
    VALUE_CHANGES("~");

    @JsonValue
    private final String value;

    ChangeType(final String value) {
        this.value = value;
    }

    /** @return {@code true} if the type is ADDED */
    public boolean isAdded() {
        return this.equals(ADDED);
    }

    /** @return {@code true} if the type is REMOVED */
    public boolean isRemoved() {
        return this.equals(REMOVED);
    }

    /** @return {@code true} if the type is VALUE_CHANGES */
    public boolean isValueChanged() {
        return this.equals(VALUE_CHANGES);
    }

    /**
     * Resolves a {@code ChangeType} from its string symbol.
     *
     * @param value the symbol to resolve (e.g., "+", "-", "~")
     * @return the matching {@code ChangeType}, or {@code null} if no match is found
     */
    @JsonCreator
    public static ChangeType fromValue(final String value) {
        for (final var changeType : ChangeType.values()) {
            if (changeType.value.equalsIgnoreCase(value)) {
                return changeType;
            }
        }
        return null;
    }
}
