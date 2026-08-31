package io.github.khezyapp.aielements.model.request;

/**
 * Marks the end of a processing step in a chat message.
 */
public record StepFinishPart(String type) implements MessagePart {
}
