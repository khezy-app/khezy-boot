package io.github.khezyapp.api.exception.custom;

import io.github.khezyapp.api.exception.data.DeveloperErrorMessage;
import lombok.Builder;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Exception thrown when a command or request conflicts with the current state of the resource.
 * <p>
 * This is typically used for 409 Conflict scenarios, such as attempting to create a record
 * with a duplicate unique identifier (e.g., email or username) or optimistic locking failures.
 * It pre-configures the {@link HttpStatus#CONFLICT} status and a specific title.
 * </p>
 */
public class ConflictException extends RestApiException {
    /**
     * The fixed title for all conflict-related errors.
     */
    private static final String TITLE = "Resource Conflict";

    /**
     * Constructs a new {@code ConflictException} using the provided builder parameters.
     * <p>
     * Automatically sets the HTTP status to {@link HttpStatus#CONFLICT} (409) and the title
     * to "Resource Conflict".
     * </p>
     *
     * @param message               the detail message or bundle key
     * @param cause                 the underlying cause
     * @param errorCode             the business-specific error code
     * @param args                  arguments for message internationalization
     * @param properties            extra metadata for the response
     * @param developerErrorMessage technical debug info
     */
    @Builder(builderMethodName = "conflictBuilder")
    public ConflictException(final String message,
                             final Throwable cause,
                             final String errorCode,
                             final Object[] args,
                             final Map<String, Object> properties,
                             final DeveloperErrorMessage developerErrorMessage) {
        super(message, TITLE, HttpStatus.CONFLICT, cause, errorCode, args, properties, developerErrorMessage);
    }
}
