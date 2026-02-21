package io.github.khezyapp.grammar.ast;

public enum ComparisonOperator {
    EQ("="),
    NE("!="),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    IN("IN"),
    BETWEEN("BETWEEN"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL");

    private final String value;

    ComparisonOperator(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ComparisonOperator of(final String value) {
        for (final var operator : ComparisonOperator.values()) {
            if (operator.value.equalsIgnoreCase(value)) {
                return operator;
            }
        }
        return null;
    }
}
