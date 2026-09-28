package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A plain-text part of a chat message. {@code state} is {@code "streaming"} or
 * {@code "done"} when the part is streamed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextPart(
        @JsonTypeId String type,
        String text,
        String state,
        Map<String, Object> providerMetadata
) implements MessagePart {

    public TextPart(final String type, final String text) {
        this(type, text, null, null);
    }
}
