package io.github.khezyapp.jooqspec;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.List;

/**
 * A specification record that aggregates jOOQ query components including WHERE, HAVING, and GROUP BY clauses.
 * <p>
 * This class implements {@link JooqCondition} by exposing the WHERE clause as the primary condition,
 * while maintaining additional metadata required for complex jOOQ query construction.
 * </p>
 *
 * @param where the primary filter condition for the WHERE clause
 * @param having the aggregate filter condition for the HAVING clause
 * @param groupBy the list of fields used for result grouping
 */
public record JooqSpecification(
        Condition where,
        Condition having,
        List<Field<?>> groupBy
) implements JooqCondition {

    /**
     * Creates a builder initialized with the current values of this specification.
     * @return a new {@link Builder} instance for mutation
     */
    public Builder mutate() {
        return new Builder()
                .where(where)
                .groupBy(groupBy)
                .having(having);
    }

    /**
     * Returns the WHERE clause condition as the primary representation of this specification.
     * @return the jOOQ {@link Condition} for filtering
     */
    public Condition condition() {
        return where;
    }

    /**
     * Fluent builder for constructing {@link JooqSpecification} instances.
     */
    public static class  Builder {
        private Condition where;
        private Condition having;
        private List<Field<?>> groupBy;

        /**
         * Sets the WHERE clause condition.
         * @param where the filter condition
         * @return the builder instance
         */
        public Builder where(final Condition where) {
            this.where = where;
            return this;
        }

        /**
         * Sets the HAVING clause condition.
         * @param having the aggregate condition
         * @return the builder instance
         */
        public Builder having(final Condition having) {
            this.having = having;
            return this;
        }

        /**
         * Sets the fields for the GROUP BY clause.
         * @param groupBy the list of grouping fields
         * @return the builder instance
         */
        public Builder groupBy(final List<Field<?>> groupBy) {
            this.groupBy = groupBy;
            return this;
        }

        /**
         * Builds a new {@link JooqSpecification} instance.
         * @return the constructed specification
         */
        public JooqSpecification build() {
            return new JooqSpecification(where, having, groupBy);
        }
    }
}
