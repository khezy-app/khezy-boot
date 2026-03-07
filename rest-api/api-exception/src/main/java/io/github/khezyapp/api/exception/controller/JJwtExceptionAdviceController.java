package io.github.khezyapp.api.exception.controller;

import io.github.khezyapp.api.exception.ErrorMessageCode;
import io.github.khezyapp.api.exception.ExceptionMessages;
import io.github.khezyapp.api.exception.data.ErrorResponse;
import io.github.khezyapp.api.exception.logging.ErrorLogger;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * Global exception handler for JSON Web Token (JWT) related failures, specifically targeting
 * exceptions thrown by the JJWT library.
 * <p>
 * This advice ensures that token validation errors, such as signature mismatches or
 * expired tokens, are translated into a standardized {@link ErrorResponse} format
 * consistent with the rest of the API.
 * </p>
 */
@RestControllerAdvice
public class JJwtExceptionAdviceController {

    private final MessageSource messageSource;
    private final ErrorLogger errorLogger;

    /**
     * Constructs the JWT exception advice with an internationalized message source and logger.
     *
     * @param messageSource the {@link MessageSource} qualified by "khezyI18nException"
     * @param errorLogger   the component responsible for logging token-related failures
     */
    public JJwtExceptionAdviceController(@Qualifier("khezyI18nException") final MessageSource messageSource,
                                         final ErrorLogger errorLogger) {
        this.messageSource = messageSource;
        this.errorLogger = errorLogger;
    }

    /**
     * Handles general {@link JwtException} instances, such as malformed tokens,
     * unsupported JWTs, or signature validation failures.
     * <p>
     * Returns a {@code 401 Unauthorized} status with a generic unauthorized message.
     * </p>
     *
     * @param e      the caught JWT exception
     * @param locale the current request locale for localized error messages
     * @return a {@code 401 Unauthorized} response entity
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(final JwtException e,
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
     * Handles {@link ExpiredJwtException}, which occurs when a JWT's expiration
     * timestamp is in the past.
     * <p>
     * Provides a specific {@code TOKEN_EXPIRED} error code to allow front-end
     * applications to trigger a refresh token flow.
     * </p>
     *
     * @param e      the caught expiration exception
     * @param locale the current request locale
     * @return a {@code 401 Unauthorized} response entity with a token-specific error code
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwtException(final ExpiredJwtException e,
                                                                   final Locale locale) {
        final var detail = messageSource.getMessage(ExceptionMessages.TOKEN_EXPIRED, null, locale);
        final var error = ErrorResponse.getCommonProperties();
        error.setTitle("Token Expired");
        error.setStatus(HttpStatus.UNAUTHORIZED.value());
        error.setErrorCode(ErrorMessageCode.TOKEN_EXPIRED.name());
        error.setDetail(detail);

        errorLogger.log(e, error);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}
