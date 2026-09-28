package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.ReasoningPart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHistoryConverterTest {

    @Test
    @DisplayName("each message gets a unique generated id, never the role as id")
    void eachMessageGetsUniqueGeneratedId() {
        final List<Message> history = List.of(
                new SystemMessage("You are helpful"),
                new UserMessage("hello"));

        final var result = ChatHistoryConverter.toChatMessages(history);

        assertEquals(2, result.size());
        final var system = result.get(0);
        final var user = result.get(1);
        assertNotNull(system.id());
        assertNotNull(user.id());
        assertNotEquals("system", system.id());
        assertNotEquals("user", user.id());
        assertNotEquals(system.id(), user.id());
    }

    @Test
    @DisplayName("metadata id is used as the message id when a store records one")
    void usesMetadataIdWhenPresent() {
        final var user = UserMessage.builder()
                .text("hello")
                .metadata(Map.of("id", "user-1"))
                .build();

        final var result = ChatHistoryConverter.toChatMessage(user);

        assertEquals("user-1", result.id());
    }

    @Test
    @DisplayName("assistant id survives the tool-result merge untouched")
    void assistantIdSurvivesMerge() {
        final var assistant = AssistantMessage.builder()
                .content("")
                .properties(Map.of("messageId", "assistant-7"))
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Siem Reap\"}")))
                .build();
        final var toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "getWeather", "{\"temp\":33}")))
                .build();

        final var result = ChatHistoryConverter.toChatMessages(
                List.of(new UserMessage("weather?"), assistant, toolResponse));

        assertEquals(2, result.size());
        assertEquals("assistant-7", result.get(1).id());
        assertEquals("assistant", result.get(1).role());
    }

    @Test
    @DisplayName("system and plain user messages map by role with text parts")
    void mapsSimpleRoles() {
        final List<Message> history = List.of(
                new SystemMessage("You are helpful"),
                new UserMessage("hello"));

        final var result = ChatHistoryConverter.toChatMessages(history);

        assertEquals(2, result.size());
        final var system = result.get(0);
        assertEquals("system", system.role());
        assertEquals("You are helpful", system.content());
        assertIsTextPart(system, "You are helpful");

        final var user = result.get(1);
        assertEquals("user", user.role());
        assertEquals("hello", user.content());
        assertIsTextPart(user, "hello");
    }

    @Test
    @DisplayName("user media maps to a base64 FilePart")
    void mapsUserMediaToFilePart() {
        final var media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType("image/png"))
                .data("abc".getBytes(StandardCharsets.UTF_8))
                .name("photo.png")
                .build();
        final var user = UserMessage.builder()
                .text("see this")
                .media(List.of(media))
                .build();

        final var result = ChatHistoryConverter.toChatMessage(user);

        assertEquals("user", result.role());
        assertEquals(2, result.parts().size());
        assertInstanceOf(TextPart.class, result.parts().get(0));
        assertInstanceOf(FilePart.class, result.parts().get(1));
        final var file = (FilePart) result.parts().get(1);
        assertEquals("file", file.type());
        assertEquals("photo.png", file.filename());
        assertEquals("image/png", file.mediaType());
        assertEquals("data:image/png;base64,YWJj", file.url());
    }

    @Test
    @DisplayName("assistant tool calls map to a dynamic ToolPart in state input-available")
    void mapsAssistantToolCalls() {
        final var assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Siem Reap\"}")))
                .build();

        final var result = ChatHistoryConverter.toChatMessage(assistant);

        assertEquals("assistant", result.role());
        final var invocation = (ToolPart) result.parts().get(0);
        assertEquals("dynamic-tool", invocation.type());
        assertEquals("call-1", invocation.toolCallId());
        assertEquals("getWeather", invocation.toolName());
        assertEquals("input-available", invocation.state());
        assertEquals(Map.of("city", "Siem Reap"), invocation.input());
    }

    @Test
    @DisplayName("tool results merge into the preceding assistant message as output-available")
    void mergesToolResultsIntoAssistant() {
        final var assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Siem Reap\"}")))
                .build();
        final var toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1", "getWeather", "{\"temp\":33}")))
                .build();

        final var result = ChatHistoryConverter.toChatMessages(
                List.of(new UserMessage("weather?"), assistant, toolResponse));

        assertEquals(2, result.size());
        final var assistantMessage = result.get(1);
        assertEquals("assistant", assistantMessage.role());
        final var invocation = (ToolPart) assistantMessage.parts().get(0);
        assertEquals("output-available", invocation.state());
        assertEquals(Map.of("temp", 33), invocation.output());
        assertEquals(Map.of("city", "Siem Reap"), invocation.input());
    }

    private static void assertIsTextPart(final ChatMessage message, final String text) {
        assertEquals(1, message.parts().size());
        final var part = message.parts().get(0);
        assertInstanceOf(TextPart.class, part);
        assertEquals(text, ((TextPart) part).text());
    }

    @Test
    @DisplayName("assistant reasoning metadata becomes a ReasoningPart before the text")
    void mapsAssistantReasoning() {
        final var assistant = AssistantMessage.builder()
                .content("answer")
                .properties(Map.of("reasoning", "let me think"))
                .build();

        final var result = ChatHistoryConverter.toChatMessage(assistant);

        final var reasoning = assertInstanceOf(ReasoningPart.class, result.parts().get(0));
        assertEquals("reasoning", reasoning.type());
        assertEquals("let me think", reasoning.text());
        assertInstanceOf(TextPart.class, result.parts().get(1));
    }

    @Test
    @DisplayName("synthetic (compaction summary) messages are excluded from history")
    void skipsSyntheticMessages() {
        final var synthetic = AssistantMessage.builder()
                .content("summary of older turns")
                .properties(Map.of("synthetic", true))
                .build();

        final var result = ChatHistoryConverter.toChatMessages(
                List.of(new UserMessage("hi"), new AssistantMessage("real answer"), synthetic));

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(message -> "summary of older turns".equals(message.content())));
    }
}
