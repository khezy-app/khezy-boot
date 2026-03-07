package io.github.khezyapp.api.exception.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

/**
 * Container for technical diagnostic information intended for developers.
 * <p>
 * This object is typically populated only in non-production environments to
 * provide additional context such as stack traces, internal hints, or raw exception messages.
 * </p>
 */
@Getter
@Builder
public class DeveloperErrorMessage {
    /**
     * The primary technical message or exception description.
     */
    private String message;

    /**
     * A map of auxiliary technical details.
     * <p>
     * Use the builder's singular method to add individual key-value pairs
     * providing further context about the internal failure.
     * </p>
     */
    @Singular
    private Map<String, Object> details;
}
