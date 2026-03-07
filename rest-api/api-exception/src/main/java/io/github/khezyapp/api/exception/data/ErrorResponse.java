package io.github.khezyapp.api.exception.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * A specialized extension of {@link org.springframework.http.ProblemDetail} used to
 * represent structured error information in API responses.
 * <p>
 * This class captures business-specific error codes, validation field errors,
 * and technical metadata for debugging. It is configured to exclude empty
 * collections or null values from the final JSON output.
 * </p>
 */
@JsonInclude(NON_EMPTY)
@Setter
@Getter
public class ErrorResponse extends ProblemDetail {
    /**
     * The unique business-level error code (e.g., "USER_NOT_FOUND").
     */
    private String errorCode;

    /**
     * A list of specific validation failures associated with request fields.
     */
    private List<FieldErrorResponse> errors;

    /**
     * Technical details intended for developers, such as stack traces or internal hints.
     */
    private DeveloperErrorMessage developer;

    /**
     * Static factory method that initializes an {@code ErrorResponse} with shared
     * diagnostic metadata extracted from the {@link org.slf4j.MDC} and the current system clock.
     * <p>
     * Populates the following properties:
     * <ul>
     * <li>{@code traceId}: The current distributed tracing identifier.</li>
     * <li>{@code spanId}: The current unit-of-work identifier.</li>
     * <li>{@code timestamp}: The exact moment the error occurred.</li>
     * </ul>
     * </p>
     *
     * @return a partially populated {@code ErrorResponse} containing common diagnostic properties
     */
    public static ErrorResponse getCommonProperties() {
        final var error = new ErrorResponse();
        error.setProperty("traceId", MDC.get("traceId"));
        error.setProperty("spanId", MDC.get("spanId"));
        error.setProperty("timestamp", Instant.now());
        return error;
    }
}
