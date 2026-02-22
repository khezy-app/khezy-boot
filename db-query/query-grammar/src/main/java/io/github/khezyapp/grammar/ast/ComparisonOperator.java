package io.github.khezyapp.grammar.ast;

/**
 * Enumeration of supported comparison operators for building specifications.
 */
public enum ComparisonOperator {
    /** Equal operator. */
    EQ("="),
    /** Not equal operator. */
    NE("!="),
    /** Less than operator. */
    LT("<"),
    /** Less than or equal operator. */
    LTE("<="),
    /** Greater than operator. */
    GT(">"),
    /** Greater than or equal operator. */
    GTE(">="),
    /** Set inclusion operator. */
    IN("IN"),
    /** Range inclusion operator. */
    BETWEEN("BETWEEN"),
    /** Null check operator. */
    IS_NULL("IS NULL"),
    /** Not null check operator. */
    IS_NOT_NULL("IS NOT NULL");

    private final String value;

    ComparisonOperator(final String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of the operator.
     * @return the operator symbol or keyword
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a {@link ComparisonOperator} from its string symbol or keyword.
     *
     * @param value the string representation to resolve
     * @return the matching operator, or {@code null} if no match is found
     */
    public static ComparisonOperator of(final String value) {
        for (final var operator : ComparisonOperator.values()) {
            if (operator.value.equalsIgnoreCase(value)) {
                return operator;
            }
        }
        return null;
    }
}
