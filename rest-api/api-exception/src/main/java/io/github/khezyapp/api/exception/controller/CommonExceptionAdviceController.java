package io.github.khezyapp.api.exception.controller;

import com.fasterxml.jackson.core.JsonParseException;
import io.github.khezyapp.api.exception.ErrorMessageCode;
import io.github.khezyapp.api.exception.ExceptionMessages;
import io.github.khezyapp.api.exception.custom.InputValidationException;
import io.github.khezyapp.api.exception.custom.RestApiException;
import io.github.khezyapp.api.exception.data.DeveloperErrorMessage;
import io.github.khezyapp.api.exception.data.ErrorResponse;
import io.github.khezyapp.api.exception.data.FieldErrorResponse;
import io.github.khezyapp.api.exception.logging.ErrorLogger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Global exception handler for the application, responsible for intercepting exceptions
 * thrown by {@code @RestController} methods and transforming them into standardized
 * {@link ErrorResponse} payloads.
 * <p>
 * This controller advice utilizes {@link MessageSource} for internationalization (i18n)
 * and an {@link ErrorLogger} for consistent diagnostic logging. It handles a variety
 * of common Spring exceptions, validation errors, and custom business exceptions.
 * </p>
 */
@RestControllerAdvice
public class CommonExceptionAdviceController {

    private final MessageSource messageSource;
    private final ErrorLogger errorLogger;

    /**
     * Constructs the controller advice with a specific i18n message source and logger.
     *
     * @param messageSource the {@link MessageSource} qualified by "khezyI18nException"
     * @param errorLogger   the component responsible for logging error details
     */
    public CommonExceptionAdviceController(@Qualifier("khezyI18nException") final MessageSource messageSource,
                                           final ErrorLogger errorLogger) {
        this.messageSource = messageSource;
        this.errorLogger = errorLogger;
    }

    /**
     * Handles unexpected {@link RuntimeException}s.
     * <p>
     * Returns a generic "Internal Server Error" response to avoid leaking sensitive
     * system details while providing a traceId for log correlation.
     * </p>
     *
     * @param e      the caught runtime exception
     * @param locale the current request locale for message translation
     * @return a {@code 500 Internal Server Error} response entity
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(final RuntimeException e,
                                                                final Locale locale) {
        final var detail = messageSource.getMessage(ExceptionMessages.INTERNAL_SERVER_ERROR, null, locale);
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle("Internal Server Error");
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        error.setErrorCode(ErrorMessageCode.UNKNOWN_ERROR.name());
        error.setDetail(detail);

        errorLogger.log(e, error);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles {@link HttpMessageNotReadableException}, typically caused by malformed JSON.
     * <p>
     * This handler inspects the underlying cause (e.g., {@code JsonParseException} or
     * {@code InvalidFormatException}) to provide specific developer-friendly hints
     * and solutions in the response.
     * </p>
     *
     * @param e      the caught exception
     * @param locale the current request locale
     * @return a {@code 400 Bad Request} response entity with diagnostic details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            final HttpMessageNotReadableException e,
            final Locale locale) {
        final var error = ErrorResponse.getCommonProperties();
        error.setStatus(HttpStatus.BAD_REQUEST);
        error.setTitle("Malformed JSON Request");

        var message = "The request body is missing or invalid.";
        final var debugErrorBuilder = DeveloperErrorMessage.builder();

        if (e.getCause() instanceof JsonParseException jpe) {
            message = messageSource.getMessage(ExceptionMessages.PARSE_JSON_ERROR, null, locale);
            debugErrorBuilder.message("JSON Syntax Error")
                    .detail("originalMessage", jpe.getOriginalMessage())
                    .detail("solution",
                            """
                                    Check for missing commas, unclosed braces {}, or unquoted keys. \
                                    Ensure you are using double quotes (") not single quotes (').""");

        } else if (e.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
            message = messageSource.getMessage(ExceptionMessages.INVALID_JSON_FORMAT,
                    new Object[]{ife.getValue(), ife.getTargetType().getSimpleName()}, locale);

            debugErrorBuilder.message("Data Type Mismatch")
                    .detail("invalidValue", ife.getValue())
                    .detail("expectedType", ife.getTargetType().getSimpleName())
                    .detail("solution",
                            String.format("""
                                            The field expects a %s. Please change the value '%s' to match the required \
                                            format (e.g., remove quotes for numbers, or use YYYY-MM-DD for dates).""",
                                    ife.getTargetType().getSimpleName(), ife.getValue()));

        } else if (e.getMessage().contains("Required request body is missing")) {
            message = messageSource.getMessage(ExceptionMessages.MISSING_REQUEST_BODY, null, locale);

            debugErrorBuilder.message("Empty Request Body")
                    .detail("solution", """
                            The @RequestBody is mandatory. Ensure your HTTP client (Postman/Frontend) is sending a \
                            'POST/PUT' request with 'Content-Type: application/json' and a non-empty JSON body {}.""");
        }

        error.setDetail(message);
        error.setDeveloper(debugErrorBuilder.build());

        errorLogger.log(e, error);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles custom {@link RestApiException}s thrown by business logic.
     *
     * @param e      the business exception
     * @param locale the current request locale
     * @return a response entity with the status and payload defined by the exception
     */
    @ExceptionHandler(RestApiException.class)
    public ResponseEntity<ErrorResponse> handleRestApiException(
            final RestApiException e,
            final Locale locale
    ) {
        final var error = getBaseErrorResponse(e, locale);
        errorLogger.log(e, error);
        return ResponseEntity.status(e.getHttpStatus())
                .body(error);
    }

    /**
     * Handles {@link MethodArgumentNotValidException}, triggered when {@code @Valid} checks fail.
     * <p>
     * Extracts field-level errors and maps them to {@link FieldErrorResponse} objects,
     * resolving the property path to identify both the field and its parent object.
     * </p>
     *
     * @param e      the validation exception
     * @param locale the current request locale
     * @return a {@code 400 Bad Request} containing a list of field validation failures
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException e,
            final Locale locale
    ) {
        final var fieldErrors = e.getBindingResult().getFieldErrors();
        final var fields = new ArrayList<FieldErrorResponse>();
        for (final var fieldError : fieldErrors) {
            final var field = new FieldErrorResponse();
            final var path = fieldError.getField().split("\\.");
            if (path.length == 1) {
                field.setField(path[0]);
                field.setObjectName("");
            } else {
                field.setField(path[path.length - 1]);
                field.setObjectName(path[path.length - 2]);
            }
            field.setReason(messageSource.getMessage(fieldError, locale));
            field.setRejectedValue(fieldError.getRejectedValue());
            field.setCode(fieldError.getCode());
            fields.add(field);
        }

        final var detail = messageSource.getMessage(ExceptionMessages.VALIDATION_ERROR,
                new Object[]{fields.size()}, locale);
        final var error = getInvalidInputErrorResponseEntity(fields, detail);
        errorLogger.log(e, error);
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles {@link InputValidationException}, typically used for manual or programmatic
     * constraint validation.
     *
     * @param e      the validation exception containing constraint violations
     * @param locale the current request locale
     * @return a {@code 400 Bad Request} detailing the specific constraint violations
     */
    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<ErrorResponse> handleInputValidationException(
            final InputValidationException e,
            final Locale locale
    ) {
        final var fields = new ArrayList<FieldErrorResponse>();
        for (final var error : e.getViolations()) {
            final var field = new FieldErrorResponse();
            field.setReason(error.getMessage());
            final var path = error.getPropertyPath().toString().split("\\.");
            if (path.length == 1) {
                field.setField(path[0]);
                field.setObjectName("");
            } else {
                field.setField(path[path.length - 1]);
                field.setObjectName(path[path.length - 2]);
            }
            field.setRejectedValue(error.getInvalidValue());
            field.setCode(error.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());

            fields.add(field);
        }

        final var detail = messageSource.getMessage(e.getMessage(), e.getArgs(), locale);
        final var error = getInvalidInputErrorResponseEntity(fields, detail);

        errorLogger.log(e, error);

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Helper method to construct an {@link ErrorResponse} for validation failures.
     *
     * @param fields the list of field errors
     * @param detail the localized summary message
     * @return a populated {@code ErrorResponse}
     */
    private ErrorResponse getInvalidInputErrorResponseEntity(
            final ArrayList<FieldErrorResponse> fields,
            final String detail
    ) {
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle(InputValidationException.TITLE);
        error.setStatus(HttpStatus.BAD_REQUEST);
        error.setErrorCode(ErrorMessageCode.INPUT_VALIDATION_ERROR.name());
        error.setDetail(detail);
        error.setErrors(fields);
        return error;
    }

    /**
     * Helper method to construct a standard {@link ErrorResponse} from a {@link RestApiException}.
     *
     * @param e      the business exception
     * @param locale the current request locale
     * @return a populated {@code ErrorResponse}
     */
    private ErrorResponse getBaseErrorResponse(final RestApiException e,
                                               final Locale locale) {
        final var message = messageSource.getMessage(e.getMessage(), e.getArgs(), locale);
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle(e.getTitle());
        error.setStatus(e.getHttpStatus());
        error.setErrorCode(e.getErrorCode());
        error.setDetail(message);
        if (Objects.nonNull(e.getProperties())) {
            error.setProperties(e.getProperties());
        }
        if (Objects.nonNull(e.getDeveloperErrorMessage())) {
            error.setDeveloper(e.getDeveloperErrorMessage());
        }
        return error;
    }
}
