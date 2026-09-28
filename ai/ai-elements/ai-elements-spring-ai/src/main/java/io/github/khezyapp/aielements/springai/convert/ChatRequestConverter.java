package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.MessagePart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolPart;
import io.github.khezyapp.aielements.model.request.UnknownPart;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
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

    private static final String TOOL_TYPE_PREFIX = "tool-";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatRequestConverter() {
    }

    /**
     * Maps a {@link ChatRequest} to a Spring AI message list, honoring trigger
     * semantics ({@code regenerate-message} trims the trailing messages).
     *
     * <p>An assistant turn that carries tool results inline expands into an
     * {@link AssistantMessage} (the calls) followed by a {@link ToolResponseMessage}
     * (the results), which is the shape Spring AI expects.</p>
     */
    public static List<Message> toSpringAiMessages(final ChatRequest request) {
        final var filtered = filterForTrigger(request);
        final var messages = new ArrayList<Message>();
        for (final var chatMessage : filtered) {
            messages.addAll(toSpringAiMessages(chatMessage));
        }
        return List.copyOf(messages);
    }

    private static List<Message> toSpringAiMessages(final ChatMessage message) {
        if ("assistant".equals(message.role())) {
            final var messages = new ArrayList<Message>();
            messages.add(toAssistantMessage(message));
            final var responses = toolResponsesOf(message);
            if (!responses.isEmpty()) {
                messages.add(ToolResponseMessage.builder().responses(responses).build());
            }
            return messages;
        }
        if ("tool".equals(message.role())) {
            final var responses = toolResponsesOf(message);
            return List.of(ToolResponseMessage.builder().responses(responses).build());
        }
        return List.of(toSpringAiMessage(message));
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
     * "tool" maps to a {@link ToolResponseMessage}.
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
            return ToolResponseMessage.builder().responses(toolResponsesOf(message)).build();
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
                media.add(toMedia(file));
            }
        }
        return List.copyOf(media);
    }

    private static Media toMedia(final FilePart file) {
        final var builder = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(file.mediaType()));
        if (isDataUrl(file.url())) {
            builder.data(decodeBase64(file.url()));
        } else {
            builder.data(URI.create(file.url()));
        }
        if (Objects.nonNull(file.filename()) && !file.filename().isBlank()) {
            builder.name(file.filename());
        }
        return builder.build();
    }

    private static boolean isDataUrl(final String url) {
        return Objects.nonNull(url) && url.startsWith("data:") && url.contains(";base64,");
    }

    private static byte[] decodeBase64(final String data) {
        if (Objects.isNull(data) || data.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(stripDataUrlPrefix(data));
    }

    private static String stripDataUrlPrefix(final String data) {
        final var marker = ";base64,";
        final int index = data.indexOf(marker);
        return index < 0 ? data : data.substring(index + marker.length());
    }

    private static AssistantMessage toAssistantMessage(final ChatMessage message) {
        final var text = textOf(message);
        final var builder = AssistantMessage.builder().content(text);
        final var toolCalls = toolCallsOf(message);
        if (!toolCalls.isEmpty()) {
            builder.toolCalls(toolCalls);
        }
        return builder.build();
    }

    private static List<AssistantMessage.ToolCall> toolCallsOf(final ChatMessage message) {
        final var calls = new ArrayList<AssistantMessage.ToolCall>();
        for (final var part : partsOf(message)) {
            final var view = toolView(part);
            if (Objects.nonNull(view) && Objects.nonNull(view.toolCallId())) {
                calls.add(new AssistantMessage.ToolCall(
                        view.toolCallId(), "function", view.toolName(), writeArgs(view.input())));
            }
        }
        return List.copyOf(calls);
    }

    private static List<ToolResponseMessage.ToolResponse> toolResponsesOf(
            final ChatMessage message) {
        final var responses = new ArrayList<ToolResponseMessage.ToolResponse>();
        for (final var part : partsOf(message)) {
            final var view = toolView(part);
            if (Objects.isNull(view) || Objects.isNull(view.toolCallId())) {
                continue;
            }
            if (Objects.nonNull(view.output()) || Objects.nonNull(view.errorText())) {
                final var payload = Objects.nonNull(view.output())
                        ? view.output() : view.errorText();
                responses.add(new ToolResponseMessage.ToolResponse(
                        view.toolCallId(), view.toolName(), writeResult(payload)));
            }
        }
        return List.copyOf(responses);
    }

    /**
     * Normalizes a tool-ish part into its id/name/input/output. Handles the
     * {@link ToolPart} ({@code dynamic-tool}) and the provider-dynamic
     * {@code tool-<name>} parts preserved as {@link UnknownPart}.
     */
    private static ToolView toolView(final MessagePart part) {
        if (part instanceof final ToolPart tool) {
            return new ToolView(
                    tool.toolCallId(),
                    Objects.nonNull(tool.toolName()) ? tool.toolName() : nameFromType(tool.type()),
                    tool.input(),
                    tool.output(),
                    tool.errorText());
        }
        if (part instanceof final UnknownPart unknown
                && Objects.nonNull(unknown.type())
                && unknown.type().startsWith(TOOL_TYPE_PREFIX)) {
            final var props = unknown.properties();
            final var toolName = Objects.nonNull(asString(props.get("toolName")))
                    ? asString(props.get("toolName")) : nameFromType(unknown.type());
            return new ToolView(
                    asString(props.get("toolCallId")),
                    toolName,
                    props.get("input"),
                    props.get("output"),
                    asString(props.get("errorText")));
        }
        return null;
    }

    private static String nameFromType(final String type) {
        return Objects.nonNull(type) && type.startsWith(TOOL_TYPE_PREFIX)
                ? type.substring(TOOL_TYPE_PREFIX.length()) : type;
    }

    private static String asString(final Object value) {
        return value instanceof final String text ? text : null;
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

    private static String writeArgs(final Object args) {
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

    private record ToolView(String toolCallId, String toolName, Object input, Object output, String errorText) {
    }
}
