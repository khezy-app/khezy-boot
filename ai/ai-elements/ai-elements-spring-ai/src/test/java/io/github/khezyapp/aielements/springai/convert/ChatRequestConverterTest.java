package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.request.ChatMessage;
import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.model.request.FilePart;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolInvocationPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestConverterTest {

    @Test
    @DisplayName("regenerate-message trigger trims from the message id onward")
    void regenerateTriggerTrimsFromMessageId() {
        final var request = new ChatRequest(
                "r1",
                List.of(
                        message("m1", "user", "first"),
                        message("m2", "user", "second"),
                        message("m3", "assistant", "third")),
                "regenerate-message",
                "m2");

        final var result = ChatRequestConverter.filterForTrigger(request);

        assertEquals(List.of("m1"), result.stream().map(ChatMessage::id).toList());

        final var converted = ChatRequestConverter.toSpringAiMessages(request);
        assertEquals(1, converted.size());
        assertEquals("first", converted.get(0).getText());
    }

    @Test
    @DisplayName("submit-message trigger keeps all messages")
    void submitTriggerKeepsAllMessages() {
        final var request = new ChatRequest(
                "r1",
                List.of(
                        message("m1", "user", "first"),
                        message("m2", "assistant", "second")),
                "submit-message",
                null);

        final var result = ChatRequestConverter.filterForTrigger(request);

        assertEquals(List.of("m1", "m2"), result.stream().map(ChatMessage::id).toList());
    }

    @Test
    @DisplayName("system/user/assistant roles map to the matching Spring AI message types")
    void systemUserAssistantRolesMapToMessageTypes() {
        final var system = ChatRequestConverter.toSpringAiMessage(message("m1", "system", "sys text"));
        final var user = ChatRequestConverter.toSpringAiMessage(message("m2", "user", "user text"));
        final var assistant = ChatRequestConverter.toSpringAiMessage(message("m3", "assistant", "assistant text"));

        assertEquals(MessageType.SYSTEM, system.getMessageType());
        assertEquals("sys text", system.getText());
        assertEquals(MessageType.USER, user.getMessageType());
        assertInstanceOf(UserMessage.class, user);
        assertTrue(((UserMessage) user).getMedia().isEmpty());
        assertEquals(MessageType.ASSISTANT, assistant.getMessageType());
    }

    @Test
    @DisplayName("user file part maps to a Media on the UserMessage")
    void userFilePartMapsToMedia() {
        final var bytes = "sample-file-bytes".getBytes(StandardCharsets.UTF_8);
        final var encoded = Base64.getEncoder().encodeToString(bytes);
        final var message = new ChatMessage(
                "m1",
                "user",
                "",
                List.of(
                        new TextPart("text", "describe this image"),
                        new FilePart("file", "photo.png", "image/png", encoded)));

        final var result = ChatRequestConverter.toSpringAiMessage(message);

        assertInstanceOf(UserMessage.class, result);
        final var user = (UserMessage) result;
        assertEquals("describe this image", user.getText());
        assertEquals(1, user.getMedia().size());
        final var media = user.getMedia().get(0);
        assertEquals("image/png", media.getMimeType().toString());
        assertEquals("photo.png", media.getName());
        assertEquals("sample-file-bytes", new String(media.getDataAsByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("user file part with a data-url prefix is base64-decoded")
    void userFilePartWithDataUrlPrefixIsDecoded() {
        final var encoded = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        final var message = new ChatMessage(
                "m1",
                "user",
                "",
                List.of(new FilePart("file", "doc.txt", "text/plain", "data:text/plain;base64," + encoded)));

        final var result = ChatRequestConverter.toSpringAiMessage(message);

        final var user = (UserMessage) result;
        assertEquals("hello", new String(user.getMedia().get(0).getDataAsByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("assistant tool invocation part maps to an AssistantMessage ToolCall")
    void assistantToolInvocationMapsToToolCall() {
        final var message = new ChatMessage(
                "m1",
                "assistant",
                "",
                List.of(new ToolInvocationPart(
                        "tool-invocation", "call-1", "getWeather", "call",
                        Map.of("city", "Phnom Penh"), null)));

        final var result = ChatRequestConverter.toSpringAiMessage(message);

        assertInstanceOf(AssistantMessage.class, result);
        final var assistant = (AssistantMessage) result;
        assertEquals(1, assistant.getToolCalls().size());
        final var toolCall = assistant.getToolCalls().get(0);
        assertEquals("call-1", toolCall.id());
        assertEquals("function", toolCall.type());
        assertEquals("getWeather", toolCall.name());
        assertEquals("{\"city\":\"Phnom Penh\"}", toolCall.arguments());
    }

    @Test
    @DisplayName("tool result part maps to a ToolResponseMessage")
    void toolResultPartMapsToToolResponseMessage() {
        final var message = new ChatMessage(
                "m1",
                "tool",
                "",
                List.of(new ToolInvocationPart(
                        "tool-invocation", "call-1", "getWeather", "result",
                        Map.of("city", "Phnom Penh"), Map.of("temp", 33))));

        final var result = ChatRequestConverter.toSpringAiMessage(message);

        assertInstanceOf(ToolResponseMessage.class, result);
        final var responseMessage = (ToolResponseMessage) result;
        assertEquals(1, responseMessage.getResponses().size());
        final var response = responseMessage.getResponses().get(0);
        assertEquals("call-1", response.id());
        assertEquals("getWeather", response.name());
        assertTrue(response.responseData().contains("\"temp\""));
    }

    private static ChatMessage message(final String id, final String role, final String text) {
        return new ChatMessage(id, role, text, List.of(new TextPart("text", text)));
    }
}
