package io.github.khezyapp.aielements.model.request;

import java.util.List;

/**
 * A chat completion request. {@code trigger} is one of {@code "submit-message"},
 * {@code "regenerate-message"} or {@code "resume-stream"}.
 */
public record ChatRequest(
        String id,
        List<ChatMessage> messages,
        String trigger,
        String messageId
) {
}
