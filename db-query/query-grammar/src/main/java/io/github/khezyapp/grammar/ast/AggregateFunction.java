package io.github.khezyapp.grammar.ast;

/**
 * Supported aggregate functions for use in expressions and specifications.
 */
public enum AggregateFunction {
    /** The COUNT aggregate function. */
    COUNT("COUNT"),
    /** The SUM aggregate function. */
    SUM("SUM"),
    /** The AVG aggregate function. */
    AVG("AVG"),
    /** The MIN aggregate function. */
    MIN("MIN"),
    /** The MAX aggregate function. */
    MAX("MAX");

    private final String value;

    AggregateFunction(final String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of the aggregate function.
     * @return the function name
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves an {@link AggregateFunction} from a string value, ignoring case.
     *
     * @param value the string value to resolve
     * @return the matching aggregate function, or {@code null} if no match is found
     */
    public static AggregateFunction of(final String value) {
        for (final var aggregate : AggregateFunction.values()) {
            if (aggregate.name().equalsIgnoreCase(value)) {
                return aggregate;
            }
        }
        return null;
    }
}
