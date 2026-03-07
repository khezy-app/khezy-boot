package io.github.khezyapp.api.exception.custom;

import io.github.khezyapp.api.exception.ErrorMessageCode;
import io.github.khezyapp.api.exception.data.DeveloperErrorMessage;
import lombok.Builder;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Exception thrown when a requested resource (e.g., a database entity) cannot be located.
 * <p>
 * This exception pre-configures a {@code 404 Not Found} HTTP status and automatically
 * associates the {@link ErrorMessageCode#RESOURCE_NOT_FOUND} business error code.
 * It is commonly used in service layers when a lookup by ID or unique key fails.
 * </p>
 */
public class ResourceNotFoundException extends RestApiException {
    /**
     * The fixed HTTP status for this exception, indicating a missing resource.
     */
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    /**
     * The fixed title used for the standardized error response.
     */
    private static final String TITLE = "Resource not found";

    /**
     * Constructs a new {@code ResourceNotFoundException} using the specialized builder.
     * <p>
     * Automatically assigns the title "Resource not found", the HTTP status 404,
     * and the specific error code for resource lookup failures.
     * </p>
     *
     * @param message               the detail message or bundle key explaining which resource is missing
     * @param cause                 the underlying cause, if any
     * @param args                  arguments for message internationalization (e.g., the ID of the missing resource)
     * @param properties            additional metadata to include in the error response extensions
     * @param developerErrorMessage technical debug info for non-production environments
     */
    @Builder(builderMethodName = "resourceNotFoundBuilder")
    public ResourceNotFoundException(final String message,
                                     final Throwable cause,
                                     final Object[] args,
                                     final Map<String, Object> properties,
                                     final DeveloperErrorMessage developerErrorMessage) {
        super(message, TITLE, STATUS, cause, ErrorMessageCode.RESOURCE_NOT_FOUND.name(),
                args, properties, developerErrorMessage);
    }
}
