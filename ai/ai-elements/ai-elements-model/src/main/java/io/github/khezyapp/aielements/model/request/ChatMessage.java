package io.github.khezyapp.aielements.model.request;

import java.util.List;

/**
 * A single chat message. {@code role} is one of {@code "user"}, {@code "assistant"} or
 * {@code "system"}; {@code content} is the legacy plain-text form (may be empty) and
 * {@code parts} is the structured form.
 */
public record ChatMessage(
        String id,
        String role,
        String content,
        List<MessagePart> parts
) {
}
