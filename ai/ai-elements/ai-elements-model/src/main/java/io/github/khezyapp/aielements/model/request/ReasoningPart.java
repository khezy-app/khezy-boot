package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;

/**
 * A reasoning (chain-of-thought) part of a chat message. {@code text} carries the
 * reasoning; {@code state} is {@code "streaming"} or {@code "done"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningPart(
        @JsonTypeId String type,
        String id,
        String text,
        String state,
        Map<String, Object> providerMetadata
) implements MessagePart {

    public ReasoningPart(final String type, final String text) {
        this(type, null, text, null, null);
    }
}
