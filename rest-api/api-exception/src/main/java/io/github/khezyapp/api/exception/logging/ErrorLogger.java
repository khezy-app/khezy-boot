package io.github.khezyapp.api.exception.logging;

import io.github.khezyapp.api.exception.data.ErrorResponse;

/**
 * Strategy interface for logging exceptions captured by the global exception handling mechanism.
 * <p>
 * Implementations of this interface define how errors are recorded (e.g., to standard logs,
 * external monitoring systems, or audit tables) after they have been processed
 * into an {@link ErrorResponse}.
 * </p>
 */
public interface ErrorLogger {

    /**
     * Logs the provided exception and its corresponding error response.
     *
     * @param ex            the original exception that was thrown
     * @param errorResponse the structured response object generated for the API client
     */
    void log(Exception ex, ErrorResponse errorResponse);
}
