package io.github.khezyapp.grammar.ast;

public enum LogicalOperator {
    AND("AND"),
    OR("OR");

    private final String value;

    LogicalOperator(final String value) {
        this.value = value;
    }

    public static LogicalOperator of(final String value) {
        for (final var logical : LogicalOperator.values()) {
            if (logical.name().equalsIgnoreCase(value)) {
                return logical;
            }
        }
        return null;
    }
}
