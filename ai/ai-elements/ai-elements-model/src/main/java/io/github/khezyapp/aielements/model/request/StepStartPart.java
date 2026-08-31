package io.github.khezyapp.aielements.model.request;

/**
 * Marks the start of a processing step in a chat message.
 */
public record StepStartPart(String type) implements MessagePart {
}
