package io.github.khezyapp.a2a.coredef.client.card;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * One cached agent card and its freshness metadata.
 *
 * @param card the cached card
 * @param header the validators returned with the card
 * @param fetchedAt the instant the card was received or revalidated
 * @param ttl how long the entry stays fresh
 */
public record AgentCardCacheEntry(AgentCard card, CardCacheHeader header,
                                  Instant fetchedAt, Duration ttl) {

    /**
     * Creates a cache entry.
     */
    public AgentCardCacheEntry {
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(header, "header must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must not be negative");
        }
    }

    /**
     * Returns the instant at which this entry becomes stale.
     *
     * @return the expiry instant
     */
    public Instant expiresAt() {
        return fetchedAt.plus(ttl);
    }

    /**
     * Returns whether this entry is still fresh.
     *
     * @param now the current instant
     * @return {@code true} while {@code now} is before the expiry instant
     */
    public boolean isFresh(final Instant now) {
        return now.isBefore(expiresAt());
    }

    /**
     * Returns the request validators for a conditional fetch.
     *
     * @return the cache header
     */
    public CardCacheHeader cardCacheHeader() {
        return header;
    }

    /**
     * Returns a refreshed copy of this entry.
     *
     * @param newTtl the new time-to-live
     * @param now the revalidation instant
     * @return the refreshed entry
     */
    public AgentCardCacheEntry refreshed(final Duration newTtl, final Instant now) {
        return new AgentCardCacheEntry(card, header, now, newTtl);
    }
}
