package io.github.khezyapp.aielements.model.response;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;

import java.util.Objects;

/**
 * A single Server-Sent Event line for the ai-elements chat stream. Each event produces
 * a {@code type()} and a minified JSON {@code json()} payload (no {@code data:}
 * prefix); {@link #toWireFormat()} wraps it in the {@code data: ...} framing. The
 * {@code [DONE]} sentinel is produced by {@link #done()}.
 */
public sealed interface SseEvent permits
        SseEvent.Start, SseEvent.StartStep, SseEvent.TextStart, SseEvent.TextDelta,
        SseEvent.TextEnd, SseEvent.ReasoningStart, SseEvent.ReasoningDelta,
        SseEvent.ReasoningEnd, SseEvent.SourceUrl, SseEvent.ToolInputStart,
        SseEvent.ToolInputAvailable, SseEvent.ToolOutputAvailable,
        SseEvent.FinishStep, SseEvent.Finish, SseEvent.ErrorEvent {

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

    record ToolInputStart(String toolCallId, String toolName) implements SseEvent {
        @Override
        public String type() {
            return "tool-input-start";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-input-start\",\"toolCallId\":\"" + escape(toolCallId)
                    + "\",\"toolName\":\"" + escape(toolName) + "\"}";
        }
    }

    record ToolInputAvailable(String toolCallId, String toolName, String inputJson)
            implements SseEvent {
        @Override
        public String type() {
            return "tool-input-available";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-input-available\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"toolName\":\"" + escape(toolName)
                    + "\",\"inputJson\":" + inputJson + "}";
        }
    }

    record ToolOutputAvailable(String toolCallId, String outputJson) implements SseEvent {
        @Override
        public String type() {
            return "tool-output-available";
        }

        @Override
        public String json() {
            return "{\"type\":\"tool-output-available\",\"toolCallId\":\""
                    + escape(toolCallId) + "\",\"output\":" + outputJson + "}";
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
            final var reasoning = Objects.nonNull(usage.reasoningTokens())
                    ? ",\"reasoningTokens\":" + usage.reasoningTokens() : "";
            final var cached = Objects.nonNull(usage.cachedInputTokens())
                    ? ",\"cachedInputTokens\":" + usage.cachedInputTokens() : "";
            return "{\"type\":\"finish\",\"finishReason\":\"" + finishReason.value()
                    + "\",\"usage\":{\"inputTokens\":" + usage.inputTokens()
                    + ",\"outputTokens\":" + usage.outputTokens()
                    + ",\"totalTokens\":" + usage.totalTokens()
                    + reasoning + cached + "}}";
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
