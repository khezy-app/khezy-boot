package io.github.khezyapp.api.exception.controller;

import io.github.khezyapp.api.exception.ErrorMessageCode;
import io.github.khezyapp.api.exception.ExceptionMessages;
import io.github.khezyapp.api.exception.data.ErrorResponse;
import io.github.khezyapp.api.exception.logging.ErrorLogger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * Global exception handler specifically for security-related exceptions, ensuring that
 * authentication and authorization failures return standardized {@link ErrorResponse} payloads.
 * <p>
 * This controller advice intercepts Spring Security exceptions that propagate to the
 * web layer, mapping them to {@code 401 Unauthorized} or {@code 403 Forbidden} statuses.
 * </p>
 */
@RestControllerAdvice
public class AuthExceptionAdviceController {

    private final MessageSource messageSource;
    private final ErrorLogger errorLogger;

    /**
     * Constructs the security exception advice with a localized message source and logger.
     *
     * @param messageSource the {@link MessageSource} qualified by "khezyI18nException"
     * @param errorLogger   the component responsible for logging security-related failures
     */
    public AuthExceptionAdviceController(@Qualifier("khezyI18nException") final MessageSource messageSource,
                                         final ErrorLogger errorLogger) {
        this.messageSource = messageSource;
        this.errorLogger = errorLogger;
    }

    /**
     * Handles {@link AuthenticationException} which occurs when a user fails to provide
     * valid credentials or is not authenticated.
     * <p>
     * Maps to an {@code ErrorResponse} with {@link HttpStatus#UNAUTHORIZED}.
     * </p>
     *
     * @param e      the caught authentication exception
     * @param locale the current request locale for localized error messages
     * @return a {@code 401 Unauthorized} response entity
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(final AuthenticationException e,
                                                                       final Locale locale) {
        final var detail = messageSource.getMessage(ExceptionMessages.UNAUTHORIZED_ERROR, null, locale);
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle("Unauthorized Request");
        error.setStatus(HttpStatus.UNAUTHORIZED.value());
        error.setErrorCode(ErrorMessageCode.UNAUTHORIZED.name());
        error.setDetail(detail);

        errorLogger.log(e, error);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handles {@link AccessDeniedException} which occurs when an authenticated user
     * attempts to access a resource for which they lack the required permissions/roles.
     * <p>
     * Maps to an {@code ErrorResponse} with {@link HttpStatus#FORBIDDEN}.
     * </p>
     *
     * @param e      the caught access denied exception
     * @param locale the current request locale
     * @return a {@code 403 Forbidden} response entity
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAAccessDeniedException(final AccessDeniedException e,
                                                                      final Locale locale) {
        final var detail = messageSource.getMessage(ExceptionMessages.FORBIDDEN_ERROR, null, locale);
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle("Access Denied");
        error.setStatus(HttpStatus.FORBIDDEN.value());
        error.setErrorCode(ErrorMessageCode.FORBIDDEN.name());
        error.setDetail(detail);

        errorLogger.log(e, error);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
