package io.github.khezyapp.aielements.model.request;

/**
 * A reasoning (chain-of-thought) part of a chat message.
 */
public record ReasoningPart(String type, String text) implements MessagePart {
}
