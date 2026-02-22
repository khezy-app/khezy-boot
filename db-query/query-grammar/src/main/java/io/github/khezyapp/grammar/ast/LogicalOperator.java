package io.github.khezyapp.grammar.ast;

/**
 * Enumeration of logical operators used to combine multiple specifications.
 */
public enum LogicalOperator {
    /** Represents the logical AND operator. */
    AND("AND"),
    /** Represents the logical OR operator. */
    OR("OR");

    private final String value;

    LogicalOperator(final String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of the logical operator.
     * @return the operator name
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a {@link LogicalOperator} from a string value, ignoring case.
     *
     * @param value the string value to resolve
     * @return the matching logical operator, or {@code null} if no match is found
     */
    public static LogicalOperator of(final String value) {
        for (final var logical : LogicalOperator.values()) {
            if (logical.name().equalsIgnoreCase(value)) {
                return logical;
            }
        }
        return null;
    }
}
