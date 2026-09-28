package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A file part of a chat message, matching the AI SDK {@code FileUIPart} shape.
 * {@code url} is either a hosted URL or a data URL
 * ({@code data:<mediaType>;base64,<payload>}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FilePart(
        @JsonTypeId String type,
        String mediaType,
        String filename,
        String url,
        Map<String, Object> providerMetadata
) implements MessagePart {
}
