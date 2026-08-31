package io.github.khezyapp.aielements.model.request;

/**
 * A file part of a chat message.
 */
public record FilePart(String type, String name, String mimeType, String data) implements MessagePart {
}
