package io.github.khezyapp.grammar.ast;

/**
 * Enumeration of supported SQL join types for data relationships within a specification.
 */
public enum JoinType {
    /** Left outer join. */
    LEFT("LEFT JOIN"),
    /** Inner join. */
    INNER("JOIN"),
    /** Right outer join. */
    RIGHT("RIGHT JOIN");

    private final String value;

    JoinType(final String value) {
        this.value = value;
    }

    /**
     * Gets the SQL string representation of the join type.
     * @return the join keyword
     */
    public String getValue() {
        return value;
    }

    /**
     * Resolves a {@link JoinType} from its name, ignoring case.
     * @param value the string name to resolve
     * @return the matching join type, or {@code null} if no match is found
     */
    public static JoinType of(final String value) {
        for (final var joinType : JoinType.values()) {
            if (joinType.name().equalsIgnoreCase(value)) {
                return joinType;
            }
        }
        return null;
    }
}
