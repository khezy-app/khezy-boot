package io.github.khezyapp.a2a.coredef.client.card;

/**
 * Fetches an agent card for a base URL.
 *
 * <p>Implementations must never throw: failures are reported through
 * {@link CardFetch#failed()} so callers can serve a stale card.</p>
 */
@FunctionalInterface
public interface AgentCardSource {

    /**
     * Fetches the agent card, optionally using conditional request headers.
     *
     * @param baseUrl the agent base URL
     * @param cacheHeader the cached validators, or {@link CardCacheHeader#empty()}
     * @return the fetch result, never {@code null}
     */
    CardFetch fetch(String baseUrl, CardCacheHeader cacheHeader);
}
