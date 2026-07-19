package io.github.khezyapp.api.security.registry;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import io.github.khezyapp.api.security.api.RowLevelSecurityRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that stores and resolves named {@link RowLevelSecurityRule} instances.
 * Rules are indexed by uppercased name and applied via {@link #enableFilter(String, KhezySecurityExpressionRoot, RowLevelSecurity, Object...)}.
 */
public class RowLevelSecurityRuleRegistry {
    private final Map<String, RowLevelSecurityRule> rules = new ConcurrentHashMap<>();

    /**
     * Populates the registry from a list of discovered {@link RowLevelSecurityRule} beans.
     *
     * @param discoveryRules the rules to register (must not be null)
     */
    public RowLevelSecurityRuleRegistry(final List<RowLevelSecurityRule> discoveryRules) {
        Objects.requireNonNull(discoveryRules, "rules cannot be null");
        discoveryRules.forEach(rule -> rules.put(ruleName(rule.getName()), rule));
    }

    /**
     * Enables the row-level filter for the named rule against the given security context.
     *
     * @param ruleName          the rule name (case-insensitive)
     * @param root              the security expression root providing context
     * @param rowLevelSecurity  the annotation metadata for the filter
     * @param args              optional arguments forwarded to the rule's {@link RowLevelSecurityRule#enableFilter} method
     * @throws IllegalArgumentException if no rule is registered under the given name
     */
    public void enableFilter(final String ruleName,
                             final KhezySecurityExpressionRoot root,
                             final RowLevelSecurity rowLevelSecurity,
                             final Object... args) {
        final var rule = rules.get(ruleName(ruleName));
        if (Objects.isNull(rule)) {
            throw new IllegalArgumentException("Row level rule '%s' could not found.".formatted(ruleName));
        }
        rule.enableFilter(root, rowLevelSecurity, args);
    }

    private String ruleName(final String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}
