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
    @DisplayName("Should render finish with usage nested under messageMetadata")
    void finishRendersUsageUnderMessageMetadata() {
        final var wire = new SseEvent.Finish(FinishReason.STOP, new Usage(1, 2, 3, null, null))
                .toWireFormat();

        assertEquals("data: {\"type\":\"finish\",\"finishReason\":\"stop\","
                + "\"messageMetadata\":{\"usage\":{\"inputTokens\":1,\"outputTokens\":2,"
                + "\"totalTokens\":3}}}\n\n", wire);
    }

    @Test
    @DisplayName("Should omit usage when none was reported")
    void finishOmitsUsageWhenNull() {
        final var wire = new SseEvent.Finish(FinishReason.STOP, null).toWireFormat();

        assertEquals("data: {\"type\":\"finish\",\"finishReason\":\"stop\"}\n\n", wire);
    }

    @Test
    @DisplayName("Should return the [DONE] sentinel")
    void doneReturnsSentinel() {
        assertEquals("data: [DONE]\n\n", SseEvent.done());
    }

    @Test
    @DisplayName("Should embed tool input JSON raw under input and mark it dynamic")
    void toolInputAvailableEmbedsInputJsonRaw() {
        final var wire = new SseEvent.ToolInputAvailable("t-1", "search", "{\"q\":\"x\"}")
                .toWireFormat();

        assertEquals("data: {\"type\":\"tool-input-available\",\"toolCallId\":\"t-1\","
                + "\"toolName\":\"search\",\"input\":{\"q\":\"x\"},\"dynamic\":true}\n\n", wire);
    }

    @Test
    @DisplayName("Should render tool-output-error with escaped error text and dynamic flag")
    void toolOutputErrorRendersErrorText() {
        final var wire = new SseEvent.ToolOutputError("t-1", "boom \"bad\"").toWireFormat();

        assertEquals("data: {\"type\":\"tool-output-error\",\"toolCallId\":\"t-1\","
                + "\"errorText\":\"boom \\\"bad\\\"\",\"dynamic\":true}\n\n", wire);
    }

    @Test
    @DisplayName("Should render the additional tool, source and data chunks")
    void rendersAdditionalChunks() {
        assertEquals("data: {\"type\":\"tool-input-delta\",\"toolCallId\":\"t-1\","
                        + "\"inputTextDelta\":\"{\\\"ci\"}\n\n",
                new SseEvent.ToolInputDelta("t-1", "{\"ci").toWireFormat());
        assertEquals("data: {\"type\":\"tool-output-denied\",\"toolCallId\":\"t-1\"}\n\n",
                new SseEvent.ToolOutputDenied("t-1").toWireFormat());
        assertEquals("data: {\"type\":\"tool-approval-request\",\"approvalId\":\"a-1\","
                        + "\"toolCallId\":\"t-1\"}\n\n",
                new SseEvent.ToolApprovalRequest("a-1", "t-1").toWireFormat());
        assertEquals("data: {\"type\":\"tool-approval-response\",\"approvalId\":\"a-1\","
                + "\"approved\":true}\n\n",
                new SseEvent.ToolApprovalResponse("a-1", true).toWireFormat());
        assertEquals("data: {\"type\":\"source-document\",\"sourceId\":\"s-1\","
                        + "\"mediaType\":\"application/pdf\",\"title\":\"Doc\","
                        + "\"filename\":\"doc.pdf\"}\n\n",
                new SseEvent.SourceDocument("s-1", "application/pdf", "Doc", "doc.pdf")
                        .toWireFormat());
        assertEquals("data: {\"type\":\"file\",\"url\":\"data:image/png;base64,YWJj\","
                        + "\"mediaType\":\"image/png\"}\n\n",
                new SseEvent.FileEvent("data:image/png;base64,YWJj", "image/png")
                        .toWireFormat());
        assertEquals("data: {\"type\":\"message-metadata\","
                        + "\"messageMetadata\":{\"usage\":{\"totalTokens\":3}}}\n\n",
                new SseEvent.MessageMetadata("{\"usage\":{\"totalTokens\":3}}").toWireFormat());
        assertEquals("data: {\"type\":\"data-plan\",\"data\":{\"step\":1}}\n\n",
                new SseEvent.DataEvent("data-plan", "{\"step\":1}").toWireFormat());
        assertEquals("data: {\"type\":\"abort\",\"reason\":\"user\"}\n\n",
                new SseEvent.Abort("user").toWireFormat());
        assertEquals("data: {\"type\":\"reset-step\"}\n\n",
                new SseEvent.ResetStep().toWireFormat());
        assertEquals("data: {\"type\":\"custom\",\"kind\":\"anthropic.thinking\"}\n\n",
                new SseEvent.Custom("anthropic.thinking").toWireFormat());
    }

    @Test
    @DisplayName("Should omit title field when null in source-url")
    void sourceUrlOmitsNullTitle() {
        final var wire = new SseEvent.SourceUrl("s-1", "http://example.com", null).toWireFormat();

        assertEquals("data: {\"type\":\"source-url\",\"sourceId\":\"s-1\","
                + "\"url\":\"http://example.com\"}\n\n", wire);
    }
}
