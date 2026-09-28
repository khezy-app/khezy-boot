package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A part referencing a document source used when producing the message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceDocumentPart(
        @JsonTypeId String type,
        String sourceId,
        String mediaType,
        String title,
        String filename,
        Map<String, Object> providerMetadata
) implements MessagePart {
}
