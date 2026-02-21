package io.github.khezyapp.grammar.ast;

public enum JoinType {
    LEFT("LEFT JOIN"),
    INNER("JOIN"),
    RIGHT("RIGHT JOIN");

    private final String value;

    JoinType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static JoinType of(final String value) {
        for (final var joinType : JoinType.values()) {
            if (joinType.name().equalsIgnoreCase(value)) {
                return joinType;
            }
        }
        return null;
    }
}
