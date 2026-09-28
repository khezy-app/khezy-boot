package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonTypeId;

/**
 * Marks the start of a processing step in a chat message.
 */
public record StepStartPart(@JsonTypeId String type) implements MessagePart {
}
