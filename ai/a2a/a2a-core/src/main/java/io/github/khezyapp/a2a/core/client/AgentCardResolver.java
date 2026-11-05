package io.github.khezyapp.a2a.core.client;

import java.util.Optional;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Resolves an agent card from a base URL.
 *
 * <p>Implementations must never throw: a card that cannot be fetched, parsed, or
 * validated yields {@link Optional#empty()} so registries can fail soft.</p>
 */
@FunctionalInterface
public interface AgentCardResolver {

    /**
     * Resolves the card for an agent.
     *
     * @param baseUrl the agent base URL
     * @return the card, or empty when it cannot be resolved
     */
    Optional<AgentCard> resolve(String baseUrl);
}
