package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.MessagePart;
import io.github.khezyapp.aielements.model.request.ReasoningPart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolPart;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Translates a stored Spring AI message history ({@link List}{@code <}Message{@code >})
 * into the ai-elements {@link ChatMessage} list the client renders.
 *
 * <p>This is the history counterpart to {@link ChatRequestConverter}: whatever memory
 * backend holds the conversation, the history is ultimately a list of Spring AI
 * {@link Message}s, and this converter turns each into the ai-elements wire shape the
 * frontend already understands.</p>
 *
 * <p>The {@link ChatMessage} {@code id} is the correlation key the client uses to key
 * messages and that {@code regenerate-message} uses as the trim boundary, so it must be
 * unique per turn. Each message's id is sourced from the Spring AI {@link Message}
 * metadata (<i>"id"</i> then <i>"messageId"</i>) when a memory backend stores one, and a
 * generated unique id otherwise (Spring AI messages carry no first-class id).</p>
 *
 * <p>Tool results are merged into the assistant message that invoked them: a Spring AI
 * {@link ToolResponseMessage} arriving after an {@link AssistantMessage} flips the
 * matching {@link ToolPart} to {@code state="output-available"} and attaches the parsed
 * output, matching the AI SDK UI shape where a call and its result live in one part.</p>
 */
public final class ChatHistoryConverter {

    private static final String ID_METADATA_KEY = "id";
    private static final String MESSAGE_ID_METADATA_KEY = "messageId";
    private static final String SYNTHETIC_METADATA_KEY = "synthetic";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGS_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private ChatHistoryConverter() {
    }

    /**
     * Converts a stored history into the ordered ai-elements {@link ChatMessage} list.
     */
    public static List<ChatMessage> toChatMessages(final List<Message> history) {
        if (Objects.isNull(history)) {
            return List.of();
        }
        final var result = new ArrayList<ChatMessage>();
        for (final var message : history) {
            // Framework-generated (compaction summary) messages are internal context: exclude them
            // from the user-facing history.
            if (isSynthetic(message)) {
                continue;
            }
            if (message instanceof final UserMessage user) {
                result.add(toUserMessage(user));
            } else if (message instanceof final AssistantMessage assistant) {
                result.add(toAssistantMessage(assistant));
            } else if (message instanceof final ToolResponseMessage toolResponse) {
                mergeToolResults(result, toolResponse);
            } else if (Objects.nonNull(message)) {
                result.add(toSimpleMessage(message));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isSynthetic(final Message message) {
        final var metadata = message.getMetadata();
        return Objects.nonNull(metadata)
                && Boolean.TRUE.equals(metadata.get(SYNTHETIC_METADATA_KEY));
    }

    /**
     * Converts a single Spring AI message to a {@link ChatMessage}.
     */
    public static ChatMessage toChatMessage(final Message message) {
        if (message instanceof final UserMessage user) {
            return toUserMessage(user);
        }
        if (message instanceof final AssistantMessage assistant) {
            return toAssistantMessage(assistant);
        }
        return toSimpleMessage(message);
    }

    private static ChatMessage toUserMessage(final UserMessage message) {
        final var parts = new ArrayList<MessagePart>();
        final var text = message.getText();
        if (Objects.nonNull(text) && !text.isBlank()) {
            parts.add(new TextPart("text", text));
        }
        for (final var media : message.getMedia()) {
            parts.add(new FilePart(
                    "file",
                    media.getMimeType().toString(),
                    media.getName(),
                    toDataUrl(media),
                    null));
        }
        return new ChatMessage(messageIdOf(message), "user", text, List.copyOf(parts));
    }

    private static String toDataUrl(final Media media) {
        final var base64 = Base64.getEncoder().encodeToString(media.getDataAsByteArray());
        return "data:" + media.getMimeType() + ";base64," + base64;
    }

    private static ChatMessage toAssistantMessage(final AssistantMessage message) {
        final var text = message.getText();
        final var parts = new ArrayList<MessagePart>();
        final var reasoning = ReasoningContent.extract(message);
        if (Objects.nonNull(reasoning) && !reasoning.isBlank()) {
            parts.add(new ReasoningPart("reasoning", reasoning));
        }
        if (Objects.nonNull(text) && !text.isBlank()) {
            parts.add(new TextPart("text", text));
        }
        for (final var toolCall : message.getToolCalls()) {
            parts.add(ToolPart.call(
                    toolCall.id(),
                    toolCall.name(),
                    parseArgs(toolCall.arguments())));
        }
        return new ChatMessage(messageIdOf(message), "assistant", text, List.copyOf(parts));
    }

    private static ChatMessage toSimpleMessage(final Message message) {
        final var text = message.getText();
        final var parts = Objects.nonNull(text) && !text.isBlank()
                ? List.<MessagePart>of(new TextPart("text", text))
                : List.<MessagePart>of();
        final var role = message.getMessageType().getValue();
        return new ChatMessage(messageIdOf(message), role, text, parts);
    }

    /**
     * Resolves a {@link ChatMessage} id for a Spring AI message: the stored metadata id
     * when a memory backend records one, otherwise a generated unique id.
     */
    private static String messageIdOf(final Message message) {
        final var metadata = message.getMetadata();
        if (Objects.nonNull(metadata)) {
            for (final var key : List.of(ID_METADATA_KEY, MESSAGE_ID_METADATA_KEY)) {
                final var value = metadata.get(key);
                if (value instanceof final String id && !id.isBlank()) {
                    return id;
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    private static void mergeToolResults(final List<ChatMessage> result,
                                         final ToolResponseMessage toolResponse) {
        final var target = result.isEmpty() ? null : result.get(result.size() - 1);
        if (Objects.isNull(target)) {
            return;
        }
        final var updatedParts = new ArrayList<MessagePart>();
        for (final var part : target.parts()) {
            if (part instanceof final ToolPart tool && !tool.hasResult()) {
                final var output = findOutput(toolResponse, tool.toolCallId());
                if (Objects.nonNull(output)) {
                    updatedParts.add(new ToolPart(
                            ToolPart.TYPE,
                            tool.toolName(),
                            tool.toolCallId(),
                            ToolPart.STATE_OUTPUT_AVAILABLE,
                            tool.input(),
                            output,
                            null,
                            tool.providerExecuted(),
                            tool.title(),
                            tool.approval(),
                            tool.toolMetadata()));
                    continue;
                }
            }
            updatedParts.add(part);
        }
        result.set(result.size() - 1, new ChatMessage(
                target.id(), target.role(), target.content(), List.copyOf(updatedParts)));
    }

    private static Object findOutput(final ToolResponseMessage toolResponse,
                                     final String toolCallId) {
        for (final var response : toolResponse.getResponses()) {
            if (Objects.equals(response.id(), toolCallId)) {
                return parseResult(response.responseData());
            }
        }
        return null;
    }

    private static Object parseResult(final String json) {
        if (Objects.isNull(json) || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (final JacksonException e) {
            return json;
        }
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
