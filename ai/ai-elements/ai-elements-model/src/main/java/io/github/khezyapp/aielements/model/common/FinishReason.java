package io.github.khezyapp.aielements.model.common;

import java.util.Arrays;

/**
 * Why a model generation finished. The {@code value} is the wire string.
 */
public enum FinishReason {

    STOP("stop"),
    LENGTH("length"),
    CONTENT_FILTER("content-filter"),
    TOOL_CALLS("tool-calls"),
    ERROR("error"),
    OTHER("other");

    private final String value;

    FinishReason(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Resolves a wire value to a {@link FinishReason}, defaulting to {@link #OTHER}
     * when no constant matches.
     */
    public static FinishReason fromString(final String value) {
        return Arrays.stream(values())
                .filter(reason -> reason.value.equals(value))
                .findFirst()
                .orElse(OTHER);
    }
}
