package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A provider-specific content part, matching the AI SDK {@code CustomContentUIPart}
 * shape. {@code kind} is {@code "{provider}.{provider-type}"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomPart(
        @JsonTypeId String type,
        String kind,
        Map<String, Object> providerMetadata
) implements MessagePart {
}
