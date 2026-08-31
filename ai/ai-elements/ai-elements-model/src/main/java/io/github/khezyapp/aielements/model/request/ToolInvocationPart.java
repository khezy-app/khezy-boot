package io.github.khezyapp.aielements.model.request;

import java.util.Map;

/**
 * A tool invocation part, carrying the call arguments and, once available, the
 * result. State is one of {@code "call"}, {@code "result"} or {@code "partial-call"}; a
 * completed tool result is a {@code ToolInvocationPart} with state {@code result}
 * (there is no separate tool-result part type).
 */
public record ToolInvocationPart(
        String type,
        String toolCallId,
        String toolName,
        String state,
        Map<String, Object> args,
        Object result
) implements MessagePart {
}
