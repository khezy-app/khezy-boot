package io.github.khezyapp.aielements.model.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinishReasonTest {

    @ParameterizedTest
    @CsvSource({
            "stop, STOP",
            "length, LENGTH",
            "content-filter, CONTENT_FILTER",
            "tool-calls, TOOL_CALLS",
            "error, ERROR",
            "other, OTHER"
    })
    @DisplayName("Should map each wire value to its finish reason")
    void fromStringMapsAllValues(final String value, final FinishReason expected) {
        assertEquals(expected, FinishReason.fromString(value));
    }

    @Test
    @DisplayName("Should fall back to OTHER for unknown or null values")
    void fromStringFallsBackToOther() {
        assertEquals(FinishReason.OTHER, FinishReason.fromString("unknown-value"));
        assertEquals(FinishReason.OTHER, FinishReason.fromString(null));
    }

    @Test
    @DisplayName("Should round-trip through value() for every reason")
    void valueRoundTrips() {
        for (final var reason : FinishReason.values()) {
            assertEquals(reason, FinishReason.fromString(reason.value()));
        }
    }
}
