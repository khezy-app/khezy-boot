package io.github.khezyapp.api.security.registry;

import io.github.khezyapp.api.security.api.AuthorizationRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that stores and resolves named {@link AuthorizationRule} instances.
 * Rules are indexed by uppercased name and evaluated via
 * {@link #check(String, KhezySecurityExpressionRoot, Object...)}.
 */
public class AuthorizationRuleRegistry {
    private final Map<String, AuthorizationRule> rules = new ConcurrentHashMap<>();

    /**
     * Populates the registry from a list of discovered {@link AuthorizationRule} beans.
     * Each rule is stored under its uppercased name for case-insensitive lookup.
     *
     * @param discoveryRules the rules to register
     */
    public AuthorizationRuleRegistry(final List<AuthorizationRule> discoveryRules) {
        discoveryRules.forEach(rule -> rules.put(rule.getName().toUpperCase(Locale.ROOT), rule));
    }

    /**
     * Evaluates the named authorization rule against the given security expression root.
     *
     * @param ruleName the rule name (case-insensitive, matched against uppercased index)
     * @param root     the security expression root providing context
     * @param args     optional arguments forwarded to the rule's {@link AuthorizationRule#evaluate} method
     * @return {@code true} if the rule permits access
     * @throws IllegalArgumentException if no rule is registered under the given name
     */
    public boolean check(final String ruleName,
                         final KhezySecurityExpressionRoot root,
                         final Object... args) {
        final var rule = rules.get(ruleName.toUpperCase(Locale.ROOT));
        if (rule == null) {
            throw new IllegalArgumentException("Authorization rule not found: " + ruleName);
        }
        return rule.evaluate(root, args);
    }
}
