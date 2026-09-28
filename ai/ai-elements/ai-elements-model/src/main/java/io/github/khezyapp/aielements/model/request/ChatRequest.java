package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A chat completion request. {@code trigger} is one of {@code "submit-message"},
 * {@code "regenerate-message"} or {@code "resume-stream"}. {@code model} is an optional
 * model id the client selects (the backend falls back to its configured model when
 * absent).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(
        String id,
        List<ChatMessage> messages,
        String trigger,
        String messageId,
        String model
) {

    /**
     * Convenience constructor for the pre-{@code model} request shape.
     */
    public ChatRequest(final String id,
                       final List<ChatMessage> messages,
                       final String trigger,
                       final String messageId) {
        this(id, messages, trigger, messageId, null);
    }
}
