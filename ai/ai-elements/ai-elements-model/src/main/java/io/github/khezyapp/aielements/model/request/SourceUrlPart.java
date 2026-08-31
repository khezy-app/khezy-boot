package io.github.khezyapp.aielements.model.request;

/**
 * A part referencing a source URL used when producing the message.
 */
public record SourceUrlPart(String type, String url, String title) implements MessagePart {
}
