package io.github.khezyapp.jooqspec;

import org.jooq.Condition;

/**
 * A functional interface representing a wrapper for a jOOQ {@link Condition}.
 * <p>
 * This interface allows for abstracting the creation of database conditions,
 * making it easier to pass structured logic through various layers of the application.
 * </p>
 */
public interface JooqCondition {

    /**
     * Returns the underlying jOOQ condition.
     * @return the jOOQ {@link Condition} instance
     */
    Condition condition();
}
