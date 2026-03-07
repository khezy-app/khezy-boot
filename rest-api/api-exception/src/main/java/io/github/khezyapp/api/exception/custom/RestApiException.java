package io.github.khezyapp.api.exception.custom;

import io.github.khezyapp.api.exception.data.DeveloperErrorMessage;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

/**
 * Base exception class for representing business and API-specific errors within the application.
 * <p>
 * This exception is designed to be caught by a {@code @RestControllerAdvice} to produce
 * consistent, RFC 7807\-compliant {@link io.github.khezyapp.api.exception.data.ErrorResponse} payloads. It supports
 * internationalization through message keys and arguments, custom HTTP statuses,
 * and developer-centric debugging information.
 * </p>
 */
@Getter
public class RestApiException extends RuntimeException {
    /**
     * The default title used if no specific title is provided.
     */
    private static final String DEFAULT_TITLE = "Business Logic Error";

    /**
     * The default HTTP status code used if none is specified, defaulting to
     * {@link HttpStatus#UNPROCESSABLE_ENTITY} (422).
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    /**
     * A short, human-readable summary of the problem type.
     */
    protected String title;

    /**
     * The HTTP status code appropriate for this specific occurrence of the problem.
     */
    protected HttpStatus httpStatus;

    /**
     * A unique business-level error code used for programmatic client-side handling.
     */
    protected String errorCode;

    /**
     * Arguments used to resolve placeholders in the localized message bundle.
     */
    protected Object[] args;

    /**
     * Additional dynamic properties to be included in the error response extensions.
     */
    protected Map<String, Object> properties;

    /**
     * Detailed technical information for debugging, typically omitted in production.
     */
    protected DeveloperErrorMessage developerErrorMessage;

    /**
     * Constructs a new {@code RestApiException} using the provided builder parameters.
     *
     * @param message               the detail message (often a bundle key)
     * @param title                 a short summary of the error; defaults to "Business Logic Error"
     * @param httpStatus            the specific {@link HttpStatus}; defaults to 422
     * @param cause                 the underlying cause of the exception
     * @param errorCode             the business\-specific error code
     * @param args                  arguments for message internationalization
     * @param properties            extra metadata for the response
     * @param developerErrorMessage technical debug info
     */
    @Builder(builderMethodName = "builder")
    public RestApiException(final String message,
                            final String title,
                            final HttpStatus httpStatus,
                            final Throwable cause,
                            final String errorCode,
                            final Object[] args,
                            final Map<String, Object> properties,
                            final DeveloperErrorMessage developerErrorMessage) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = args;
        this.properties = properties;
        this.developerErrorMessage = developerErrorMessage;
        this.title = defaultIfEmpty(title, DEFAULT_TITLE);
        this.httpStatus = Objects.isNull(httpStatus) ? DEFAULT_STATUS : httpStatus;
    }

    /**
     * Utility method to provide a fallback title if the provided string is null or blank.
     *
     * @param title        the title to check
     * @param defaultTitle the fallback title
     * @return the provided title if valid, otherwise the defaultTitle
     */
    protected String defaultIfEmpty(final String title,
                                    final String defaultTitle) {
        if (Objects.isNull(title) || title.isBlank()) {
            return defaultTitle;
        }
        return title;
    }
}
