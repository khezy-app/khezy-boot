package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeId;

import java.util.Map;
import java.util.Objects;

/**
 * A tool invocation part, matching the AI SDK {@code DynamicToolUIPart} shape
 * ({@code type = "dynamic-tool"}). The client sends this when it does not have a typed
 * definition for the tool, which is the case for every server-side tool.
 *
 * <p>{@code state} is one of {@code "input-streaming"}, {@code "input-available"},
 * {@code "approval-requested"}, {@code "approval-responded"}, {@code "output-available"},
 * {@code "output-error"}, {@code "output-denied"}. The output is carried inline on the
 * same part (there is no separate tool-result part).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolPart(
        @JsonTypeId String type,
        String toolName,
        String toolCallId,
        String state,
        Object input,
        Object output,
        String errorText,
        Boolean providerExecuted,
        String title,
        Map<String, Object> approval,
        Map<String, Object> toolMetadata
) implements MessagePart {

    public static final String TYPE = "dynamic-tool";
    public static final String STATE_INPUT_STREAMING = "input-streaming";
    public static final String STATE_INPUT_AVAILABLE = "input-available";
    public static final String STATE_OUTPUT_AVAILABLE = "output-available";
    public static final String STATE_OUTPUT_ERROR = "output-error";

    /**
     * A completed tool call with its input already available and no output yet.
     */
    public static ToolPart call(final String toolCallId,
                                final String toolName,
                                final Object input) {
        return new ToolPart(TYPE, toolName, toolCallId, STATE_INPUT_AVAILABLE,
                input, null, null, null, null, null, null);
    }

    /**
     * A completed tool call whose result is available.
     */
    public static ToolPart result(final String toolCallId,
                                  final String toolName,
                                  final Object input,
                                  final Object output) {
        return new ToolPart(TYPE, toolName, toolCallId, STATE_OUTPUT_AVAILABLE,
                input, output, null, null, null, null, null);
    }

    /**
     * Whether this part carries a tool result (as opposed to only a call).
     */
    public boolean hasResult() {
        return STATE_OUTPUT_AVAILABLE.equals(state)
                || STATE_OUTPUT_ERROR.equals(state)
                || Objects.nonNull(output) || Objects.nonNull(errorText);
    }
}
