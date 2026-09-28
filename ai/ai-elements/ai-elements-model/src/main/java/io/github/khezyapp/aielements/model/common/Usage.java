package io.github.khezyapp.aielements.model.common;

/**
 * Token usage for a generation. Token counts are integers; reasoning and cached-input
 * counts may be absent ({@code null}).
 */
public record Usage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer reasoningTokens,
        Integer cachedInputTokens
) {

    /**
     * A zeroed usage with no reasoning or cached-input counts.
     */
    public static Usage empty() {
        return new Usage(0, 0, 0, null, null);
    }
}
