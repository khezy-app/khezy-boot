package io.github.khezyapp.a2a.coredef.client.card;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.a2aproject.sdk.spec.AgentCard;

import io.github.khezyapp.a2a.core.client.AgentCardResolver;

/**
 * Spec-compliant agent-card cache.
 *
 * <p>A fresh entry is returned without I/O. A stale entry is revalidated with
 * {@code If-None-Match}/{@code If-Modified-Since}; a {@code 304} reuses the cached
 * card. A failed revalidation serves the stale card when one exists. The resolver
 * never throws.</p>
 */
public class CachingAgentCardResolver implements AgentCardResolver {

    /**
     * The default cache time-to-live.
     */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final AgentCardSource source;
    private final Duration defaultTtl;
    private final Clock clock;
    private final ConcurrentMap<String, AgentCardCacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Creates a resolver with the default five-minute TTL and the system clock.
     *
     * @param source the card source
     */
    public CachingAgentCardResolver(final AgentCardSource source) {
        this(source, DEFAULT_TTL, Clock.systemUTC());
    }

    /**
     * Creates a resolver.
     *
     * @param source the card source
     * @param defaultTtl the TTL used when the response carries no {@code max-age}
     * @param clock the clock used for freshness checks
     */
    public CachingAgentCardResolver(final AgentCardSource source, final Duration defaultTtl, final Clock clock) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
        if (defaultTtl.isNegative()) {
            throw new IllegalArgumentException("defaultTtl must not be negative");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Optional<AgentCard> resolve(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        final String key = normalize(baseUrl);
        final AgentCardCacheEntry cached = cache.get(key);
        final Instant now = clock.instant();
        if (cached != null && cached.isFresh(now)) {
            return Optional.of(cached.card());
        }

        final CardCacheHeader validators = cached == null
                ? CardCacheHeader.empty()
                : cached.cardCacheHeader();
        final CardFetch fetch;
        try {
            fetch = source.fetch(baseUrl, validators);
        } catch (RuntimeException e) {
            return serveStale(cached);
        }
        if (fetch == null) {
            return serveStale(cached);
        }

        if (fetch.isOk()) {
            cache.put(key, new AgentCardCacheEntry(
                    fetch.card(),
                    new CardCacheHeader(fetch.etag(), fetch.lastModified()),
                    now,
                    ttl(fetch)));
            return Optional.of(fetch.card());
        }
        if (fetch.isNotModified() && cached != null) {
            final var merged = new CardCacheHeader(
                    firstNonBlank(fetch.etag(), cached.header().etag()),
                    firstNonBlank(fetch.lastModified(), cached.header().lastModified()));
            final var refreshed = new AgentCardCacheEntry(cached.card(), merged, now, ttl(fetch));
            cache.put(key, refreshed);
            return Optional.of(cached.card());
        }
        return serveStale(cached);
    }

    /**
     * Removes one cached card.
     *
     * @param baseUrl the agent base URL
     */
    public void invalidate(final String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            cache.remove(normalize(baseUrl));
        }
    }

    /**
     * Removes every cached card.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of cached entries.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }

    private Optional<AgentCard> serveStale(final AgentCardCacheEntry cached) {
        return cached == null ? Optional.empty() : Optional.of(cached.card());
    }

    private Duration ttl(final CardFetch fetch) {
        final var maxAge = fetch.maxAge();
        if (maxAge == null || maxAge.isNegative()) {
            return defaultTtl;
        }
        return maxAge;
    }

    private static String firstNonBlank(final String first, final String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static String normalize(final String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
