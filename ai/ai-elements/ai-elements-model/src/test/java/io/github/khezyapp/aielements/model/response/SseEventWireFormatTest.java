package io.github.khezyapp.aielements.model.response;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseEventWireFormatTest {

    @Test
    @DisplayName("Should escape quotes and newlines in text-delta")
    void textDeltaEscapesQuotesAndNewlines() {
        final var wire = new SseEvent.TextDelta("0", "a\"b\nc").toWireFormat();

        assertEquals("data: {\"type\":\"text-delta\",\"id\":\"0\","
                + "\"delta\":\"a\\\"b\\nc\"}\n\n", wire);
    }

    @Test
    @DisplayName("Should omit messageId field when null in start")
    void startWithNullMessageIdOmitsField() {
        final var wire = new SseEvent.Start(null).toWireFormat();

        assertEquals("data: {\"type\":\"start\"}\n\n", wire);
    }

    @Test
    @DisplayName("Should include messageId field when present in start")
    void startWithMessageIdIncludesField() {
        final var wire = new SseEvent.Start("m-1").toWireFormat();

        assertEquals("data: {\"type\":\"start\",\"messageId\":\"m-1\"}\n\n", wire);
    }

    @Test
    @DisplayName("Should render finish with usage inline")
    void finishRendersUsageInline() {
        final var wire = new SseEvent.Finish(FinishReason.STOP, new Usage(1, 2, 3, null, null))
                .toWireFormat();

        assertEquals("data: {\"type\":\"finish\",\"finishReason\":\"stop\","
                + "\"usage\":{\"inputTokens\":1,\"outputTokens\":2,\"totalTokens\":3}}\n\n", wire);
    }

    @Test
    @DisplayName("Should return the [DONE] sentinel")
    void doneReturnsSentinel() {
        assertEquals("data: [DONE]\n\n", SseEvent.done());
    }

    @Test
    @DisplayName("Should embed tool input JSON raw, unescaped")
    void toolInputAvailableEmbedsInputJsonRaw() {
        final var wire = new SseEvent.ToolInputAvailable("t-1", "search", "{\"q\":\"x\"}")
                .toWireFormat();

        assertEquals("data: {\"type\":\"tool-input-available\",\"toolCallId\":\"t-1\","
                + "\"toolName\":\"search\",\"inputJson\":{\"q\":\"x\"}}\n\n", wire);
    }

    @Test
    @DisplayName("Should omit title field when null in source-url")
    void sourceUrlOmitsNullTitle() {
        final var wire = new SseEvent.SourceUrl("s-1", "http://example.com", null).toWireFormat();

        assertEquals("data: {\"type\":\"source-url\",\"sourceId\":\"s-1\","
                + "\"url\":\"http://example.com\"}\n\n", wire);
    }
}
