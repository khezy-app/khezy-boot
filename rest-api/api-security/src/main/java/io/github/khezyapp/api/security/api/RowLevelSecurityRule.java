package io.github.khezyapp.api.security.api;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;

/**
 * Strategy interface for dynamic Row-Level Security logic.
 * Provides a programmatic way to enable Hibernate filters based on security context.
 */
public interface RowLevelSecurityRule {

    String getName();

    /** Callback to execute {@code session.enableFilter(...)} using provided context. */
    void enableFilter(KhezySecurityExpressionRoot root,
                      RowLevelSecurity rowLevelSecurity,
                      Object[] args);

}
