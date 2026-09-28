package io.github.khezyapp.aielements.model.response;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;

import java.util.ArrayList;
import java.util.List;

/**
 * Transport-free accumulator that builds an ordered {@link SseEvent} stream, ready to
 * be emitted over any SSE transport. {@link #build()} returns the immutable event
 * list; {@link #buildWireFormat()} renders the full wire stream ending in
 * {@code [DONE]}.
 */
public final class SseEventBuilder {

    private final List<SseEvent> events = new ArrayList<>();

    public SseEventBuilder start(final String messageId) {
        events.add(new SseEvent.Start(messageId));
        return this;
    }

    public SseEventBuilder startStep() {
        events.add(new SseEvent.StartStep());
        return this;
    }

    public SseEventBuilder text(final String id,
                                final String text) {
        events.add(new SseEvent.TextStart(id));
        events.add(new SseEvent.TextDelta(id, text));
        events.add(new SseEvent.TextEnd(id));
        return this;
    }

    public SseEventBuilder textDelta(final String id,
                                     final String delta) {
        events.add(new SseEvent.TextDelta(id, delta));
        return this;
    }

    public SseEventBuilder reasoning(final String id,
                                     final String text) {
        events.add(new SseEvent.ReasoningStart(id));
        events.add(new SseEvent.ReasoningDelta(id, text));
        events.add(new SseEvent.ReasoningEnd(id));
        return this;
    }

    public SseEventBuilder sourceUrl(final String sourceId,
                                     final String url,
                                     final String title) {
        events.add(new SseEvent.SourceUrl(sourceId, url, title));
        return this;
    }

    public SseEventBuilder sourceDocument(final String sourceId,
                                          final String mediaType,
                                          final String title,
                                          final String filename) {
        events.add(new SseEvent.SourceDocument(sourceId, mediaType, title, filename));
        return this;
    }

    public SseEventBuilder file(final String url,
                                final String mediaType) {
        events.add(new SseEvent.FileEvent(url, mediaType));
        return this;
    }

    public SseEventBuilder reasoningFile(final String url,
                                         final String mediaType) {
        events.add(new SseEvent.ReasoningFileEvent(url, mediaType));
        return this;
    }

    public SseEventBuilder toolInputStart(final String toolCallId,
                                          final String toolName) {
        events.add(new SseEvent.ToolInputStart(toolCallId, toolName));
        return this;
    }

    public SseEventBuilder toolInputDelta(final String toolCallId,
                                          final String inputTextDelta) {
        events.add(new SseEvent.ToolInputDelta(toolCallId, inputTextDelta));
        return this;
    }

    public SseEventBuilder toolInputAvailable(
            final String toolCallId,
            final String toolName,
            final String input
    ) {
        events.add(new SseEvent.ToolInputAvailable(toolCallId, toolName, input));
        return this;
    }

    public SseEventBuilder toolInputError(final String toolCallId,
                                          final String toolName,
                                          final String input,
                                          final String errorText) {
        events.add(new SseEvent.ToolInputError(toolCallId, toolName, input, errorText));
        return this;
    }

    public SseEventBuilder toolOutputAvailable(final String toolCallId,
                                               final String outputJson) {
        events.add(new SseEvent.ToolOutputAvailable(toolCallId, outputJson));
        return this;
    }

    public SseEventBuilder toolOutputError(final String toolCallId,
                                           final String errorText) {
        events.add(new SseEvent.ToolOutputError(toolCallId, errorText));
        return this;
    }

    public SseEventBuilder toolOutputDenied(final String toolCallId) {
        events.add(new SseEvent.ToolOutputDenied(toolCallId));
        return this;
    }

    public SseEventBuilder toolApprovalRequest(final String approvalId,
                                               final String toolCallId) {
        events.add(new SseEvent.ToolApprovalRequest(approvalId, toolCallId));
        return this;
    }

    public SseEventBuilder toolApprovalResponse(final String approvalId,
                                                final boolean approved) {
        events.add(new SseEvent.ToolApprovalResponse(approvalId, approved));
        return this;
    }

    public SseEventBuilder finish(final FinishReason reason,
                                  final Usage usage) {
        events.add(new SseEvent.FinishStep());
        events.add(new SseEvent.Finish(reason, usage));
        return this;
    }

    public SseEventBuilder metadata(final String metadataJson) {
        events.add(new SseEvent.MessageMetadata(metadataJson));
        return this;
    }

    public SseEventBuilder resetStep() {
        events.add(new SseEvent.ResetStep());
        return this;
    }

    public SseEventBuilder custom(final String kind) {
        events.add(new SseEvent.Custom(kind));
        return this;
    }

    public SseEventBuilder data(final String type,
                                final String dataJson) {
        events.add(new SseEvent.DataEvent(type, dataJson));
        return this;
    }

    public SseEventBuilder abort(final String reason) {
        events.add(new SseEvent.Abort(reason));
        return this;
    }

    public SseEventBuilder error(final String errorText) {
        events.add(new SseEvent.ErrorEvent(errorText));
        return this;
    }

    public List<SseEvent> build() {
        return List.copyOf(events);
    }

    public String buildWireFormat() {
        final var sb = new StringBuilder();
        for (final var event : events) {
            sb.append(event.toWireFormat());
        }
        sb.append(SseEvent.done());
        return sb.toString();
    }
}
