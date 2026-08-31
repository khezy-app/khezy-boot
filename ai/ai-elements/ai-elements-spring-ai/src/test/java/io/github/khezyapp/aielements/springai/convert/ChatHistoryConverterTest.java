package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolInvocationPart;
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
        assertEquals("photo.png", file.name());
        assertEquals("image/png", file.mimeType());
        assertEquals("YWJj", file.data());
    }

    @Test
    @DisplayName("assistant tool calls map to ToolInvocationPart with state call")
    void mapsAssistantToolCalls() {
        final var assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Siem Reap\"}")))
                .build();

        final var result = ChatHistoryConverter.toChatMessage(assistant);

        assertEquals("assistant", result.role());
        final var invocation = (ToolInvocationPart) result.parts().get(0);
        assertEquals("tool-invocation", invocation.type());
        assertEquals("call-1", invocation.toolCallId());
        assertEquals("getWeather", invocation.toolName());
        assertEquals("call", invocation.state());
        assertEquals(Map.of("city", "Siem Reap"), invocation.args());
    }

    @Test
    @DisplayName("tool results merge into the preceding assistant message as state result")
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
        final var invocation = (ToolInvocationPart) assistantMessage.parts().get(0);
        assertEquals("result", invocation.state());
        assertEquals(Map.of("temp", 33), invocation.result());
    }

    private static void assertIsTextPart(final ChatMessage message, final String text) {
        assertEquals(1, message.parts().size());
        final var part = message.parts().get(0);
        assertInstanceOf(TextPart.class, part);
        assertEquals(text, ((TextPart) part).text());
    }
}
