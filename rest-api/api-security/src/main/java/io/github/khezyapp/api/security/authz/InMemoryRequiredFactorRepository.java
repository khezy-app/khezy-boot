package io.github.khezyapp.api.security.authz;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple in-memory implementation of {@link RequiredFactorAuthoritiesRepository}
 * backed by a {@link java.util.Map}. Useful for testing or static configuration
 * where factor requirements are known at startup.
 */
public class InMemoryRequiredFactorRepository implements RequiredFactorAuthoritiesRepository {
    private final Map<String, List<String>> requiredFactors;

    /**
     * Creates a repository with the given user-to-factors mapping.
     *
     * @param requiredFactors map of username to the list of factor authorities required
     */
    public InMemoryRequiredFactorRepository(final Map<String, List<String>> requiredFactors) {
        this.requiredFactors = requiredFactors;
    }

    /**
     * Returns the list of factor authorities required for the given username,
     * or an empty list if the user has no configured requirements.
     */
    @Override
    public List<String> findRequiredFactorAuthorities(final String username) {
        return requiredFactors.getOrDefault(username, Collections.emptyList());
    }
}
