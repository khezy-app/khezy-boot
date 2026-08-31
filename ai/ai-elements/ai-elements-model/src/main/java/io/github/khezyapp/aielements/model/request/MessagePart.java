package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A single part of a chat message, discriminated by the {@code type} JSON field.
 *
 * <p>This is the AI SDK UI-stream part union. Tool results are <em>not</em> a
 * part type on the wire; they travel as a {@link ToolInvocationPart} with state
 * {@code result} instead.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
        @JsonSubTypes.Type(value = SourceUrlPart.class, name = "source-url"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = ToolInvocationPart.class, name = "tool-invocation"),
        @JsonSubTypes.Type(value = StepStartPart.class, name = "step-start"),
        @JsonSubTypes.Type(value = StepFinishPart.class, name = "step-finish"),
})
public sealed interface MessagePart permits TextPart, ReasoningPart, SourceUrlPart,
        FilePart, ToolInvocationPart, StepStartPart, StepFinishPart {
}
