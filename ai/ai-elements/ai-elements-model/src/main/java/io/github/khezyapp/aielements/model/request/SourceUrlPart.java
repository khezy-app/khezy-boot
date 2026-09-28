package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A part referencing a source URL used when producing the message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceUrlPart(
        @JsonTypeId String type,
        String sourceId,
        String url,
        String title,
        Map<String, Object> providerMetadata
) implements MessagePart {
}
