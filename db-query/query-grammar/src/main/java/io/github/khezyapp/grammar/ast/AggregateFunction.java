package io.github.khezyapp.grammar.ast;

public enum AggregateFunction {
    COUNT("COUNT"),
    SUM("SUM"),
    AVG("AVG"),
    MIN("MIN"),
    MAX("MAX");

    private final String value;

    AggregateFunction(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AggregateFunction of(final String value) {
        for (final var aggregate : AggregateFunction.values()) {
            if (aggregate.name().equalsIgnoreCase(value)) {
                return aggregate;
            }
        }
        return null;
    }
}
