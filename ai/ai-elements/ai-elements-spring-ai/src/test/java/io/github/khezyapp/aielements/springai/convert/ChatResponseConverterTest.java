package io.github.khezyapp.aielements.springai.convert;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.request.TextPart;
import io.github.khezyapp.aielements.model.request.ToolPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatResponseConverterTest {

    @Test
    @DisplayName("Spring AI usage maps prompt/completion/total and cache-read to input/output/total/cached-input")
    void mapsUsageFields() {
        final var springUsage = new DefaultUsage(10, 20, 30);

        final var usage = ChatResponseConverter.toUsage(springUsage);

        assertEquals(10, usage.inputTokens());
        assertEquals(20, usage.outputTokens());
        assertEquals(30, usage.totalTokens());
        assertNull(usage.reasoningTokens());
        assertNull(usage.cachedInputTokens());
    }

    @Test
    @DisplayName("cache read tokens map to cachedInputTokens")
    void mapsCacheReadTokensToCachedInputTokens() {
        final var springUsage = new DefaultUsage(10, 20, 30, null, 7L, 4L);

        final var usage = ChatResponseConverter.toUsage(springUsage);

        assertEquals(10, usage.inputTokens());
        assertEquals(20, usage.outputTokens());
        assertEquals(30, usage.totalTokens());
        assertNull(usage.reasoningTokens());
        assertEquals(7, usage.cachedInputTokens());
    }

    @Test
    @DisplayName("finish reason maps via fromString, unknown and null map to OTHER")
    void mapsFinishReasonViaFromString() {
        assertEquals(FinishReason.STOP, ChatResponseConverter.toFinishReason("stop"));
        assertEquals(FinishReason.TOOL_CALLS, ChatResponseConverter.toFinishReason("tool-calls"));
        assertEquals(FinishReason.OTHER, ChatResponseConverter.toFinishReason("some-unknown-reason"));
        assertEquals(FinishReason.OTHER, ChatResponseConverter.toFinishReason(null));
    }

    @Test
    @DisplayName("assistant text and tool calls become TextPart and a dynamic ToolPart")
    void assistantTextAndToolCallsBecomeParts() {
        final var assistant = AssistantMessage.builder()
                .content("Here is the weather")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-1", "function", "getWeather", "{\"city\":\"Siem Reap\"}")))
                .build();

        final var parts = ChatResponseConverter.toParts(assistant);

        assertEquals(2, parts.size());
        assertInstanceOf(TextPart.class, parts.get(0));
        assertEquals("Here is the weather", ((TextPart) parts.get(0)).text());

        assertInstanceOf(ToolPart.class, parts.get(1));
        final var toolPart = (ToolPart) parts.get(1);
        assertEquals("dynamic-tool", toolPart.type());
        assertEquals("call-1", toolPart.toolCallId());
        assertEquals("getWeather", toolPart.toolName());
        assertEquals("input-available", toolPart.state());
        assertEquals(Map.of("city", "Siem Reap"), toolPart.input());
        assertNull(toolPart.output());
    }

    @Test
    @DisplayName("toChatMessage builds an assistant ChatMessage from a ChatResponse")
    void toChatMessageBuildsAssistantMessage() {
        final var assistant = AssistantMessage.builder()
                .content("Hello there")
                .build();
        final var generation = new Generation(assistant);
        final var response = new ChatResponse(List.of(generation));

        final var chatMessage = ChatResponseConverter.toChatMessage(response, "msg-1");

        assertEquals("msg-1", chatMessage.id());
        assertEquals("assistant", chatMessage.role());
        assertEquals("Hello there", chatMessage.content());
        assertEquals(1, chatMessage.parts().size());
        assertInstanceOf(TextPart.class, chatMessage.parts().get(0));
    }

    @Test
    @DisplayName("toUsage maps a null Spring usage to empty usage")
    void toUsageHandlesNull() {
        final var usage = ChatResponseConverter.toUsage(null);
        assertEquals(0, usage.inputTokens());
        assertEquals(0, usage.outputTokens());
        assertEquals(0, usage.totalTokens());
    }
}
