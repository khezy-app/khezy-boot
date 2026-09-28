package io.github.khezyapp.aielements.model.response;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;

import java.util.Objects;

/**
 * A single Server-Sent Event line for the ai-elements chat stream. Each event produces
 * a {@code type()} and a minified JSON {@code json()} payload (no {@code data:}
 * prefix); {@link #toWireFormat()} wraps it in the {@code data: ...} framing. The
 * {@code [DONE]} sentinel is produced by {@link #done()}.
 *
 * <p>This mirrors the AI SDK UI-message-stream chunk union. Values that the AI SDK
 * carries as raw JSON (tool input/output, data payloads, metadata) are passed to this
 * model as pre-serialized JSON strings and embedded unescaped.</p>
 */
public sealed interface SseEvent permits
        SseEvent.Start, SseEvent.StartStep, SseEvent.TextStart, SseEvent.TextDelta,
        SseEvent.TextEnd, SseEvent.ReasoningStart, SseEvent.ReasoningDelta,
        SseEvent.ReasoningEnd, SseEvent.SourceUrl, SseEvent.SourceDocument,
        SseEvent.FileEvent, SseEvent.ReasoningFileEvent, SseEvent.ToolInputStart,
        SseEvent.ToolInputDelta, SseEvent.ToolInputAvailable, SseEvent.ToolInputError,
        SseEvent.ToolApprovalRequest, SseEvent.ToolApprovalResponse,
        SseEvent.ToolOutputAvailable, SseEvent.ToolOutputError,
        SseEvent.ToolOutputDenied, SseEvent.FinishStep, SseEvent.Finish,
        SseEvent.MessageMetadata, SseEvent.ResetStep, SseEvent.Custom, SseEvent.DataEvent,
        SseEvent.Abort, SseEvent.ErrorEvent {

    String type();

    String json();

    default String toWireFormat() {
        return "data: " + json() + "\n\n";
    }

    static String done() {
        return "data: [DONE]\n\n";
    }

    record Start(String messageId) implements SseEvent {
        @Override
        public String type() {
            return "start";
        }

        @Override
        public String json() {
            final var id = messageId != null
                    ? ",\"messageId\":\"" + escape(messageId) + "\"" : "";
            return "{\"type\":\"start\"" + id + "}";
        }
    }

    record StartStep() implements SseEvent {
        @Override
        public String type() {
            return "start-step";
        }

        @Override
        public String json() {
            return "{\"type\":\"start-step\"}";
        }
    }

    record TextStart(String id) implements SseEvent {
        @Override
        public String type() {
            return "text-start";
        }

        @Override
        public String json() {
            return "{\"type\":\"text-start\",\"id\":\"" + escape(id) + "\"}";
        }
    }

    record TextDelta(String id, String delta) implements SseEvent {
        @Override
        public String type() {
            return "text-delta";
        }

        @Override
        public String json() {
            return "{\"type\":\"text-delta\",\"id\":\"" + escape(id) + "\",\"delta\":\""
                    + escape(delta) + "\"}";
        }
    }

    record TextEnd(String id) implements SseEvent {
        @Override
        public String type() {
            return "text-end";
        }

        @Override
        public String json() {
            return "{\"type\":\"text-end\",\"id\":\"" + escape(id) + "\"}";
        }
    }

    record ReasoningStart(String id) implements SseEvent {
        @Override
        public String type() {
            return "reasoning-start";
        }

        @Override
        public String json() {
            return "{\"type\":\"reasoning-start\",\"id\":\"" + escape(id) + "\"}";
        }
    }

    record ReasoningDelta(String id, String delta) implements SseEvent {
        @Override
        public String type() {
            return "reasoning-delta";
        }

        @Override
        public String json() {
            return "{\"type\":\"reasoning-delta\",\"id\":\"" + escape(id) + "\",\"delta\":\""
                    + escape(delta) + "\"}";
        }
    }

    record ReasoningEnd(String id) implements SseEvent {
        @Override
        public String type() {
            return "reasoning-end";
        }

        @Override
        public String json() {
            return "{\"type\":\"reasoning-end\",\"id\":\"" + escape(id) + "\"}";
        }
    }

    record SourceUrl(String sourceId, String url, String title) implements SseEvent {
        @Override
        public String type() {
            return "source-url";
        }

        @Override
        public String json() {
            final var titleField = Objects.nonNull(title)
                    ? ",\"title\":\"" + escape(title) + "\"" : "";
            return "{\"type\":\"source-url\",\"sourceId\":\"" + escape(sourceId)
                    + "\",\"url\":\"" + escape(url) + "\"" + titleField + "}";
        }
    }

    record SourceDocument(String sourceId, String mediaType, String title, String filename)
            implements SseEvent {
        @Override
        public String type() {
            return "source-document";
        }

        @Override
        public String json() {
            final var filenameField = Objects.nonNull(filename)
                    ? ",\"filename\":\"" + escape(filename) + "\"" : "";
            return "{\"type\":\"source-document\",\"sourceId\":\"" + escape(sourceId)
                    + "\",\"mediaType\":\"" + escape(mediaType)
                    + "\",\"title\":\"" + escape(title) + "\"" + filenameField + "}";
        }
    }

    record FileEvent(String url, String mediaType) implements SseEvent {
        @Override
        public String type() {
            return "file";
        }

        @Override
        public String json() {
            return "{\"type\":\"file\",\"url\":\"" + escape(url)
                    + "\",\"mediaType\":\"" + escape(mediaType) + "\"}";
        }
    }

    record ReasoningFileEvent(String url, String mediaType) implements SseEvent {
        @Override
        public String type() {
            return "reasoning-file";
        }

        @Override
        public String json() {
            return "{\"type\":\"reasoning-file\",\"url\":\"" + escape(url)
                    + "\",\"mediaType\":\"" + escape(mediaType) + "\"}";
        }
    }

    record ToolInputStart(String toolCallId, String toolName, boolean dynamic)
            implements SseEvent {

        public ToolInputStart(final String toolCallId, final String toolName) {
            this(toolCallId, toolName, true);
        }

        @Override
        public String type() {
            return "tool-input-start";
        }

        @Override
        public String json() {
            final var dyn = dynamic ? ",\"dynamic\":true" : "";
            return "{\"type\":\"tool-input-start\",\"toolCallId\":\"" + escape(toolCallId)
                    + "\",\"toolName\":\"" + escape(toolName) + "\"" + dyn + "}";
        }
    }

    record ToolInputDelta(String toolCallId, String inputTextDelta) implements SseEvent {
        @Override
        public String type() {
            return "tool-input-delta";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-input-delta\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"inputTextDelta\":\""
                    + escape(inputTextDelta) + "\"}";
        }
    }

    record ToolInputAvailable(String toolCallId, String toolName, String input,
                              boolean dynamic) implements SseEvent {

        public ToolInputAvailable(final String toolCallId, final String toolName,
                                  final String input) {
            this(toolCallId, toolName, input, true);
        }

        @Override
        public String type() {
            return "tool-input-available";
        }

        @Override
        public String json() {
            final var dyn = dynamic ? ",\"dynamic\":true" : "";
            return "{\"type\":\"tool-input-available\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"toolName\":\"" + escape(toolName)
                    + "\",\"input\":" + input + dyn + "}";
        }
    }

    record ToolInputError(String toolCallId, String toolName, String input,
                          String errorText, boolean dynamic) implements SseEvent {

        public ToolInputError(final String toolCallId, final String toolName,
                              final String input, final String errorText) {
            this(toolCallId, toolName, input, errorText, true);
        }

        @Override
        public String type() {
            return "tool-input-error";
        }

        @Override
        public String json() {
            final var dyn = dynamic ? ",\"dynamic\":true" : "";
            return "{\"type\":\"tool-input-error\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"toolName\":\"" + escape(toolName)
                    + "\",\"input\":" + input + ",\"errorText\":\"" + escape(errorText)
                    + "\"" + dyn + "}";
        }
    }

    record ToolOutputAvailable(String toolCallId, String outputJson, boolean dynamic)
            implements SseEvent {

        public ToolOutputAvailable(final String toolCallId, final String outputJson) {
            this(toolCallId, outputJson, true);
        }

        @Override
        public String type() {
            return "tool-output-available";
        }

        @Override
        public String json() {
            final var dyn = dynamic ? ",\"dynamic\":true" : "";
            return "{\"type\":\"tool-output-available\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"output\":" + outputJson + dyn + "}";
        }
    }

    record ToolOutputError(String toolCallId, String errorText, boolean dynamic)
            implements SseEvent {

        public ToolOutputError(final String toolCallId, final String errorText) {
            this(toolCallId, errorText, true);
        }

        @Override
        public String type() {
            return "tool-output-error";
        }

        @Override
        public String json() {
            final var dyn = dynamic ? ",\"dynamic\":true" : "";
            return "{\"type\":\"tool-output-error\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"errorText\":\"" + escape(errorText)
                    + "\"" + dyn + "}";
        }
    }

    record ToolOutputDenied(String toolCallId) implements SseEvent {
        @Override
        public String type() {
            return "tool-output-denied";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-output-denied\",\"toolCallId\":\""
                    + escape(toolCallId) + "\"}";
        }
    }

    record ToolApprovalRequest(String approvalId, String toolCallId) implements SseEvent {
        @Override
        public String type() {
            return "tool-approval-request";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-approval-request\",\"approvalId\":\""
                    + escape(approvalId) + "\",\"toolCallId\":\"" + escape(toolCallId) + "\"}";
        }
    }

    record ToolApprovalResponse(String approvalId, boolean approved) implements SseEvent {
        @Override
        public String type() {
            return "tool-approval-response";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-approval-response\",\"approvalId\":\""
                    + escape(approvalId) + "\",\"approved\":" + approved + "}";
        }
    }

    record FinishStep() implements SseEvent {
        @Override
        public String type() {
            return "finish-step";
        }

        @Override
        public String json() {
            return "{\"type\":\"finish-step\"}";
        }
    }

    record Finish(FinishReason finishReason, Usage usage) implements SseEvent {
        @Override
        public String type() {
            return "finish";
        }

        @Override
        public String json() {
            if (Objects.isNull(usage)) {
                // No LLM ran this turn: omit usage entirely so clients can tell "not reported"
                // apart from a real zero.
                return "{\"type\":\"finish\",\"finishReason\":\"" + finishReason.value() + "\"}";
            }
            final var reasoning = Objects.nonNull(usage.reasoningTokens())
                    ? ",\"reasoningTokens\":" + usage.reasoningTokens() : "";
            final var cached = Objects.nonNull(usage.cachedInputTokens())
                    ? ",\"cachedInputTokens\":" + usage.cachedInputTokens() : "";
            return "{\"type\":\"finish\",\"finishReason\":\"" + finishReason.value()
                    + "\",\"messageMetadata\":{\"usage\":{\"inputTokens\":" + usage.inputTokens()
                    + ",\"outputTokens\":" + usage.outputTokens()
                    + ",\"totalTokens\":" + usage.totalTokens()
                    + reasoning + cached + "}}}";
        }
    }

    record MessageMetadata(String metadataJson) implements SseEvent {
        @Override
        public String type() {
            return "message-metadata";
        }

        @Override
        public String json() {
            return "{\"type\":\"message-metadata\",\"messageMetadata\":" + metadataJson + "}";
        }
    }

    record ResetStep() implements SseEvent {
        @Override
        public String type() {
            return "reset-step";
        }

        @Override
        public String json() {
            return "{\"type\":\"reset-step\"}";
        }
    }

    record Custom(String kind) implements SseEvent {
        @Override
        public String type() {
            return "custom";
        }

        @Override
        public String json() {
            return "{\"type\":\"custom\",\"kind\":\"" + escape(kind) + "\"}";
        }
    }

    /**
     * A provider data chunk. {@code type} is the full wire type ({@code data-<name>}).
     */
    record DataEvent(String type, String dataJson) implements SseEvent {
        @Override
        public String json() {
            return "{\"type\":\"" + escape(type) + "\",\"data\":" + dataJson + "}";
        }
    }

    record Abort(String reason) implements SseEvent {
        @Override
        public String type() {
            return "abort";
        }

        @Override
        public String json() {
            final var reasonField = Objects.nonNull(reason)
                    ? ",\"reason\":\"" + escape(reason) + "\"" : "";
            return "{\"type\":\"abort\"" + reasonField + "}";
        }
    }

    record ErrorEvent(String errorText) implements SseEvent {
        @Override
        public String type() {
            return "error";
        }

        @Override
        public String json() {
            return "{\"type\":\"error\",\"errorText\":\"" + escape(errorText) + "\"}";
        }
    }

    private static String escape(final String s) {
        if (Objects.isNull(s)) {
            return "";
        }
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
