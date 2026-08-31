package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.MessagePart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolInvocationPart;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts ai-elements {@link ChatRequest}/{@link ChatMessage} models into Spring AI
 * {@link Message} lists, honoring the {@code trigger} semantics ("regenerate-message"
 * trims the conversation from the referenced {@code messageId}).
 */
public final class ChatRequestConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatRequestConverter() {
    }

    /**
     * Maps a {@link ChatRequest} to a Spring AI message list, honoring trigger
     * semantics ({@code regenerate-message} trims the trailing messages).
     */
    public static List<Message> toSpringAiMessages(final ChatRequest request) {
        final var filtered = filterForTrigger(request);
        final var messages = new ArrayList<Message>(filtered.size());
        for (final var chatMessage : filtered) {
            messages.add(toSpringAiMessage(chatMessage));
        }
        return List.copyOf(messages);
    }

    /**
     * Trims the request per trigger: drops the {@code messageId} and everything after
     * it when {@code trigger == "regenerate-message"}; otherwise returns the messages
     * unchanged.
     */
    public static List<ChatMessage> filterForTrigger(final ChatRequest request) {
        final var messages = request.messages();
        if (!"regenerate-message".equals(request.trigger())) {
            return messages;
        }
        final var messageId = request.messageId();
        for (int i = 0; i < messages.size(); i++) {
            if (Objects.nonNull(messages.get(i).id()) && messages.get(i).id().equals(messageId)) {
                return messages.subList(0, i);
            }
        }
        return messages;
    }

    /**
     * Converts a single {@link ChatMessage} to a Spring AI {@link Message}, dispatching
     * on {@code role}. "system"/"user"/"assistant" map to their Spring AI counterparts;
     * tool results travel as a {@code ToolInvocationPart(state="result")} and map to a
     * {@link ToolResponseMessage}.
     */
    public static Message toSpringAiMessage(final ChatMessage message) {
        final var role = message.role();
        if ("system".equals(role)) {
            return new SystemMessage(textOf(message));
        }
        if ("user".equals(role)) {
            return toUserMessage(message);
        }
        if ("assistant".equals(role)) {
            return toAssistantMessage(message);
        }
        if ("tool".equals(role)) {
            return toToolResponseMessage(message);
        }
        throw new IllegalArgumentException("Unsupported chat message role: " + role);
    }

    private static UserMessage toUserMessage(final ChatMessage message) {
        final var text = textOf(message);
        final var media = mediaOf(message);
        if (media.isEmpty()) {
            return new UserMessage(text);
        }
        return UserMessage.builder().text(text).media(media).build();
    }

    private static List<Media> mediaOf(final ChatMessage message) {
        final var media = new ArrayList<Media>();
        for (final var part : partsOf(message)) {
            if (part instanceof final FilePart file) {
                media.add(Media.builder()
                        .mimeType(MimeTypeUtils.parseMimeType(file.mimeType()))
                        .data(decodeBase64(file.data()))
                        .name(file.name())
                        .build());
            }
        }
        return List.copyOf(media);
    }

    private static byte[] decodeBase64(final String data) {
        if (Objects.isNull(data) || data.isBlank()) {
            return new byte[0];
        }
        final var payload = stripDataUrlPrefix(data);
        return Base64.getDecoder().decode(payload);
    }

    private static String stripDataUrlPrefix(final String data) {
        final var marker = ";base64,";
        final var index = data.indexOf(marker);
        return index < 0 ? data : data.substring(index + marker.length());
    }

    private static AssistantMessage toAssistantMessage(final ChatMessage message) {
        final var text = textOf(message);
        final var toolCalls = new ArrayList<AssistantMessage.ToolCall>();
        for (final var part : partsOf(message)) {
            if (part instanceof final ToolInvocationPart toolPart
                    && "call".equals(toolPart.state())) {
                final var argsJson = writeArgs(toolPart.args());
                toolCalls.add(new AssistantMessage.ToolCall(
                        toolPart.toolCallId(), "function", toolPart.toolName(), argsJson));
            }
        }
        final var builder = AssistantMessage.builder().content(text);
        if (!toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls);
        }
        return builder.build();
    }

    private static ToolResponseMessage toToolResponseMessage(final ChatMessage message) {
        final var responses = new ArrayList<ToolResponseMessage.ToolResponse>();
        for (final var part : partsOf(message)) {
            if (part instanceof final ToolInvocationPart toolPart
                    && "result".equals(toolPart.state())) {
                final var resultJson = writeResult(toolPart.result());
                responses.add(new ToolResponseMessage.ToolResponse(
                        toolPart.toolCallId(), toolPart.toolName(), resultJson));
            }
        }
        return ToolResponseMessage.builder().responses(responses).build();
    }

    private static String textOf(final ChatMessage message) {
        final var parts = message.parts();
        if (Objects.nonNull(parts) && !parts.isEmpty()) {
            final var text = new StringBuilder();
            for (final var part : parts) {
                if (part instanceof final TextPart textPart) {
                    text.append(textPart.text());
                }
            }
            if (!text.isEmpty()) {
                return text.toString();
            }
        }
        return message.content();
    }

    private static List<MessagePart> partsOf(final ChatMessage message) {
        return Objects.nonNull(message.parts()) ? message.parts() : List.of();
    }

    private static String writeArgs(final Map<String, Object> args) {
        try {
            return MAPPER.writeValueAsString(Objects.nonNull(args) ? args : Map.of());
        } catch (final tools.jackson.core.JacksonException e) {
            return "{}";
        }
    }

    private static String writeResult(final Object result) {
        try {
            return MAPPER.writeValueAsString(Objects.nonNull(result) ? result : Map.of());
        } catch (final tools.jackson.core.JacksonException e) {
            return "{}";
        }
    }
}
