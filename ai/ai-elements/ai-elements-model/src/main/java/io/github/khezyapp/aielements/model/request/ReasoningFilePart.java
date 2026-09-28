package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A file attached to a reasoning block, matching the AI SDK {@code ReasoningFileUIPart}
 * shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningFilePart(
        @JsonTypeId String type,
        String mediaType,
        String url,
        Map<String, Object> providerMetadata
) implements MessagePart {
}
