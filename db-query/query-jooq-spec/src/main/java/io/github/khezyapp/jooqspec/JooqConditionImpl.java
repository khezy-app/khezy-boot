package io.github.khezyapp.jooqspec;

import org.jooq.Condition;

/**
 * A concrete implementation of {@link JooqCondition} using a Java record.
 *
 * @param condition the actual jOOQ condition to be wrapped
 */
public record JooqConditionImpl(Condition condition) implements JooqCondition {
}
