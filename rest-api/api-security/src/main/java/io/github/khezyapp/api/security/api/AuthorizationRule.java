package io.github.khezyapp.api.security.api;

import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;

/**
 * Strategy interface for complex authorization logic.
 * Implementations should encapsulate specific business permission checks.
 */
public interface AuthorizationRule {

    /** Unique identifier used in {@code check('RULE_NAME')} expressions. */
    String getName();

    /** Logic to determine if the current security state satisfies the rule. */
    boolean evaluate(KhezySecurityExpressionRoot root, Object[] args);
}
