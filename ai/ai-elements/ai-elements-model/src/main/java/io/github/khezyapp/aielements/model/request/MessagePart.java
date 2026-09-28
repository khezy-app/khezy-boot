package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A single part of a chat message, discriminated by the {@code type} JSON field.
 *
 * <p>This is the AI SDK UI-message part union. Tool results are <em>not</em> a separate
 * part type: a tool invocation carries its output inline once available.</p>
 *
 * <p>The AI SDK also defines provider-dynamic types whose names cannot be enumerated
 * statically ({@code tool-<name>}, {@code data-<name>}). Those deserialize into
 * {@link UnknownPart}, which preserves every raw field; the converters interpret the
 * {@code tool-*} ones. The {@code type} discriminator is visible so it is also bound to
 * the part's own {@code type} component, which is annotated {@code @JsonTypeId} to keep
 * it written exactly once.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type",
        visible = true, defaultImpl = UnknownPart.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
        @JsonSubTypes.Type(value = SourceUrlPart.class, name = "source-url"),
        @JsonSubTypes.Type(value = SourceDocumentPart.class, name = "source-document"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = ReasoningFilePart.class, name = "reasoning-file"),
        @JsonSubTypes.Type(value = StepStartPart.class, name = "step-start"),
        @JsonSubTypes.Type(value = ToolPart.class, name = "dynamic-tool"),
        @JsonSubTypes.Type(value = CustomPart.class, name = "custom"),
})
public sealed interface MessagePart permits TextPart, ReasoningPart, SourceUrlPart,
        SourceDocumentPart, FilePart, ReasoningFilePart, StepStartPart, ToolPart,
        CustomPart, UnknownPart {

    /**
     * The wire {@code type} discriminator of this part.
     */
    String type();
}
