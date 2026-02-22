package io.github.khezyapp.jooqspec;

import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.impl.DSL;

/**
 * Represents a sorting definition for a jOOQ query, mapping a field name and direction to a jOOQ SortField.
 *
 * @param field the name of the field to sort by
 * @param direction the direction of the sort (ASC or DESC)
 */
public record JooqOrder(
        String field,
        Direction direction
) {

    /**
     * Converts this order definition into a jOOQ {@link SortField}.
     * @return a sort field expression compatible with jOOQ select clauses
     */
    public SortField<Object> getSortField() {
        return DSL.field(field).sort(SortOrder.valueOf(direction.name()));
    }

    /**
     * Supported sort directions.
     */
    public enum Direction {
        /** Ascending sort order. */
        ASC,
        /** Descending sort order. */
        DESC;

        /**
         * Resolves a {@link Direction} from a string value, ignoring case.
         *
         * @param direction the string representation (e.g., "asc", "DESC")
         * @return the matching Direction
         * @throws IllegalArgumentException if the direction is not recognized
         */
        public static Direction of(final String direction) {
            for (Direction d : Direction.values()) {
                if (d.name().equalsIgnoreCase(direction)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }
}
