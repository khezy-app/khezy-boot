package io.github.khezyapp.aielements.springai.convert;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts a chunk's reasoning ("thinking") text from a Spring AI {@link AssistantMessage}, which
 * providers expose in different, non-portable ways.
 *
 * <p>Known patterns are checked first (cheap metadata lookups); only when none matches does it fall
 * back to a cached reflective lookup of a {@code getReasoningContent()} accessor, so
 * provider-specific message types (e.g. DeepSeek's reasoner message) are still supported without
 * coupling this library to their SDKs.</p>
 */
public final class ReasoningContent {

    private static final String IS_THOUGHT = "isThought";
    private static final String THINKING = "thinking";
    private static final String REASONING_CONTENT = "reasoningContent";
    private static final String REASONING = "reasoning";
    private static final String REASONING_CONTENT_ACCESSOR = "getReasoningContent";

    private static final Map<Class<?>, Optional<Method>> ACCESSOR_CACHE = new ConcurrentHashMap<>();

    private ReasoningContent() {
    }

    /**
     * The reasoning text carried by this chunk, or {@code null} when the chunk is not a reasoning
     * chunk. Content-flagged reasoning (Anthropic/Gemini) returns the message text; string-metadata
     * reasoning (OpenAI) returns the metadata value; provider-specific messages are read reflectively.
     */
    public static String extract(final AssistantMessage message) {
        if (Objects.isNull(message)) {
            return null;
        }
        final var metadata = message.getMetadata();
        if (Objects.nonNull(metadata)) {
            // Content-flagged reasoning: the thinking text is the message content.
            if (Boolean.TRUE.equals(metadata.get(IS_THOUGHT)) || Boolean.TRUE.equals(metadata.get(THINKING))) {
                return text(message);
            }
            // String-metadata reasoning: the thinking text is a metadata value.
            final var reasoningContent = nonEmptyString(metadata.get(REASONING_CONTENT));
            if (Objects.nonNull(reasoningContent)) {
                return reasoningContent;
            }
            final var reasoning = nonEmptyString(metadata.get(REASONING));
            if (Objects.nonNull(reasoning)) {
                return reasoning;
            }
            final var thinking = nonEmptyString(metadata.get(THINKING));
            if (Objects.nonNull(thinking)) {
                return thinking;
            }
        }
        return reflectReasoningContent(message);
    }

    /**
     * True when the reasoning is carried as the message content itself (Anthropic/Gemini
     * content-flagged reasoning), i.e. {@code getText()} must not also be emitted as a text block.
     */
    public static boolean isContentFlagged(final AssistantMessage message) {
        if (Objects.isNull(message)) {
            return false;
        }
        final var metadata = message.getMetadata();
        return Objects.nonNull(metadata)
                && (Boolean.TRUE.equals(metadata.get(IS_THOUGHT))
                        || Boolean.TRUE.equals(metadata.get(THINKING)));
    }

    private static String text(final AssistantMessage message) {
        final var content = message.getText();
        return Objects.nonNull(content) && !content.isEmpty() ? content : null;
    }

    private static String nonEmptyString(final Object value) {
        return value instanceof String string && !string.isEmpty() ? string : null;
    }

    private static String reflectReasoningContent(final AssistantMessage message) {
        final var accessor = ACCESSOR_CACHE.computeIfAbsent(message.getClass(), ReasoningContent::findAccessor);
        if (accessor.isEmpty()) {
            return null;
        }
        try {
            return nonEmptyString(accessor.get().invoke(message));
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    private static Optional<Method> findAccessor(final Class<?> type) {
        try {
            final var method = type.getMethod(REASONING_CONTENT_ACCESSOR);
            return method.getReturnType() == String.class ? Optional.of(method) : Optional.empty();
        } catch (final NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
