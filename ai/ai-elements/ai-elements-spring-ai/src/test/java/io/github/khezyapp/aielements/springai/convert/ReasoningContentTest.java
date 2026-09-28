package io.github.khezyapp.aielements.springai.convert;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReasoningContentTest {

    @Test
    void extractsAnthropicThinkingFromContentFlag() {
        final var message = AssistantMessage.builder()
            .content("let me think")
            .properties(Map.of("thinking", true))
            .build();

        assertEquals("let me think", ReasoningContent.extract(message));
    }

    @Test
    void extractsGeminiThoughtFromContentFlag() {
        final var message = AssistantMessage.builder()
            .content("reasoning text")
            .properties(Map.of("isThought", true))
            .build();

        assertEquals("reasoning text", ReasoningContent.extract(message));
    }

    @Test
    void extractsOpenAiReasoningContentFromMetadata() {
        final var message = AssistantMessage.builder()
            .properties(Map.of("reasoningContent", "openai reasoning"))
            .build();

        assertEquals("openai reasoning", ReasoningContent.extract(message));
    }

    @Test
    void extractsOpenAiResponsesReasoningFromMetadata() {
        final var message = AssistantMessage.builder()
            .properties(Map.of("reasoning", "responses reasoning"))
            .build();

        assertEquals("responses reasoning", ReasoningContent.extract(message));
    }

    @Test
    void extractsProviderSpecificReasoningReflectively() {
        assertEquals("deepseek reasoning", ReasoningContent.extract(new FakeReasoningMessage("deepseek reasoning")));
    }

    @Test
    void returnsNullForPlainText() {
        assertNull(ReasoningContent.extract(AssistantMessage.builder().content("hello").build()));
        assertNull(ReasoningContent.extract(null));
    }

    @Test
    void returnsNullWhenContentFlagHasNoText() {
        assertNull(ReasoningContent.extract(AssistantMessage.builder()
            .properties(Map.of("thinking", true))
            .build()));
    }

    /**
     * Mimics a provider message type (e.g. DeepSeek) that exposes reasoning through a public
     * {@code getReasoningContent()} accessor rather than metadata.
     */
    private static final class FakeReasoningMessage extends AssistantMessage {

        private final String reasoning;

        private FakeReasoningMessage(final String reasoning) {
            super(null);
            this.reasoning = reasoning;
        }

        public String getReasoningContent() {
            return reasoning;
        }
    }
}
