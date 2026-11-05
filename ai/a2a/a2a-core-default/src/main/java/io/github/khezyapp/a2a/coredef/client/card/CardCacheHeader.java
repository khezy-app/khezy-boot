package io.github.khezyapp.a2a.coredef.client.card;

/**
 * HTTP cache validators for an agent card.
 *
 * @param etag the {@code ETag} value, or {@code null}
 * @param lastModified the {@code Last-Modified} value, or {@code null}
 */
public record CardCacheHeader(String etag, String lastModified) {

    private static final CardCacheHeader EMPTY = new CardCacheHeader(null, null);

    /**
     * Returns the empty header set.
     *
     * @return the empty header set
     */
    public static CardCacheHeader empty() {
        return EMPTY;
    }

    /**
     * Returns whether no validator is present.
     *
     * @return {@code true} when both values are blank
     */
    public boolean isEmpty() {
        return isBlank(etag) && isBlank(lastModified);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
