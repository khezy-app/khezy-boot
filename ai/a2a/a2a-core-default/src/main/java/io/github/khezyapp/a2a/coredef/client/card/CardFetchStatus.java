package io.github.khezyapp.a2a.coredef.client.card;

/**
 * Outcome of one agent-card fetch.
 */
public enum CardFetchStatus {

    /**
     * A card was fetched and parsed.
     */
    OK,

    /**
     * The server accepted the cached validators with {@code 304 Not Modified}.
     */
    NOT_MODIFIED,

    /**
     * The card could not be fetched or parsed.
     */
    FAILED
}
