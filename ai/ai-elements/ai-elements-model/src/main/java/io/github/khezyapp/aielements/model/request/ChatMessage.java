package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A single chat message. {@code role} is one of {@code "user"}, {@code "assistant"} or
 * {@code "system"}; {@code content} is the legacy plain-text form (may be empty) and
 * {@code parts} is the structured form.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessage(
        String id,
        String role,
        String content,
        List<MessagePart> parts
) {
}
