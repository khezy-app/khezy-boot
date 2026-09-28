package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.MessagePart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolPart;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts Spring AI {@link ChatResponse} results and metadata into ai-elements
 * {@link ChatMessage} models, mapping usage, finish reason, text and tool calls.
 */
public final class ChatResponseConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private ChatResponseConverter() {
    }

    /**
     * Non-streaming: {@link ChatResponse} to a server-side {@link ChatMessage} the UI
     * can render.
     */
    public static ChatMessage toChatMessage(final ChatResponse response,
                                            final String id) {
        final var output = response.getResult().getOutput();
        return new ChatMessage(id, "assistant", output.getText(), toParts(output));
    }

    /**
     * Spring AI {@link org.springframework.ai.chat.metadata.Usage} to ai-elements
     * {@link Usage} (prompt-&gt;input, completion-&gt;output, total-&gt;total,
     * cache-read-&gt;cached-input).
     *
     * <p>{@code reasoningTokens} is always {@code null}: Spring AI's {@code Usage}
     * interface exposes no portable accessor for reasoning tokens (they only appear in
     * provider-specific {@code getNativeUsage()}), so the value cannot be mapped without
     * coupling to a specific provider.</p>
     */
    public static Usage toUsage(final org.springframework.ai.chat.metadata.Usage usage) {
        if (Objects.isNull(usage)) {
            return Usage.empty();
        }
        return new Usage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                null,
                toInteger(usage.getCacheReadInputTokens())
        );
    }

    private static Integer toInteger(final Long value) {
        return Objects.isNull(value) ? null : value.intValue();
    }

    /**
     * Spring AI finish-reason string to {@link FinishReason} (null-safe, unknown maps
     * to {@link FinishReason#OTHER}).
     */
    public static FinishReason toFinishReason(final String springAiReason) {
        return FinishReason.fromString(springAiReason);
    }

    /**
     * {@link AssistantMessage} text + tool calls to a {@link List} of {@link MessagePart}
     * ({@link TextPart} + a {@link ToolPart} in state {@code "input-available"}).
     */
    public static List<MessagePart> toParts(final AssistantMessage message) {
        final var parts = new ArrayList<MessagePart>();
        final var text = message.getText();
        if (Objects.nonNull(text) && !text.isBlank()) {
            parts.add(new TextPart("text", text));
        }
        for (final var toolCall : message.getToolCalls()) {
            parts.add(
                ToolPart.call(
                    toolCall.id(),
                    toolCall.name(),
                    parseArgs(toolCall.arguments())
                )
            );
        }
        return List.copyOf(parts);
    }

    private static Map<String, Object> parseArgs(final String argumentsJson) {
        if (Objects.isNull(argumentsJson) || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            final var parsed = MAPPER.readValue(argumentsJson, ARGS_TYPE);
            return Objects.nonNull(parsed) ? parsed : Map.of();
        } catch (final JacksonException e) {
            return Map.of();
        }
    }
}
