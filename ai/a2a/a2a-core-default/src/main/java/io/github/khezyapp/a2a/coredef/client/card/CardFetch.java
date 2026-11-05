package io.github.khezyapp.a2a.coredef.client.card;

import java.time.Duration;
import java.util.Objects;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * Result of one agent-card fetch.
 *
 * @param status the fetch status
 * @param card the parsed card, present for {@link CardFetchStatus#OK}
 * @param etag the response {@code ETag}, or {@code null}
 * @param lastModified the response {@code Last-Modified}, or {@code null}
 * @param maxAge the response {@code Cache-Control: max-age}, or {@code null}
 */
public record CardFetch(CardFetchStatus status, AgentCard card, String etag,
                        String lastModified, Duration maxAge) {

    /**
     * Creates a fetch result.
     */
    public CardFetch {
        Objects.requireNonNull(status, "status must not be null");
        if (status == CardFetchStatus.OK) {
            Objects.requireNonNull(card, "card must not be null for an OK fetch");
        }
    }

    /**
     * Creates a successful fetch.
     *
     * @param card the parsed card
     * @param etag the response ETag
     * @param lastModified the response Last-Modified
     * @param maxAge the response max-age
     * @return the fetch result
     */
    public static CardFetch ok(final AgentCard card, final String etag,
                               final String lastModified, final Duration maxAge) {
        return new CardFetch(CardFetchStatus.OK, card, etag, lastModified, maxAge);
    }

    /**
     * Creates a not-modified result.
     *
     * @param etag the response ETag
     * @param lastModified the response Last-Modified
     * @param maxAge the response max-age
     * @return the fetch result
     */
    public static CardFetch notModified(final String etag, final String lastModified, final Duration maxAge) {
        return new CardFetch(CardFetchStatus.NOT_MODIFIED, null, etag, lastModified, maxAge);
    }

    /**
     * Creates a failed result.
     *
     * @return the fetch result
     */
    public static CardFetch failed() {
        return new CardFetch(CardFetchStatus.FAILED, null, null, null, null);
    }

    /**
     * Returns whether a card was fetched.
     *
     * @return {@code true} for {@link CardFetchStatus#OK}
     */
    public boolean isOk() {
        return status == CardFetchStatus.OK;
    }

    /**
     * Returns whether the cache is still valid.
     *
     * @return {@code true} for {@link CardFetchStatus#NOT_MODIFIED}
     */
    public boolean isNotModified() {
        return status == CardFetchStatus.NOT_MODIFIED;
    }
}
