package io.github.khezyapp.aielements.model.request;

/**
 * A plain-text part of a chat message.
 */
public record TextPart(String type, String text) implements MessagePart {
}
