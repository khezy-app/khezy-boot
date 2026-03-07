package io.github.khezyapp.api.exception.custom;

import io.github.khezyapp.api.exception.ErrorMessageCode;
import io.github.khezyapp.api.exception.ExceptionMessages;
import jakarta.validation.ConstraintViolation;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Exception thrown when programmatic validation fails, capturing a set of constraint violations.
 * <p>
 * This exception is typically used when manually invoking the {@code jakarta.validation.Validator}
 * outside of standard Spring {@code @Valid} processing. It pre-configures a
 * {@link HttpStatus#BAD_REQUEST} (400) and provides a structured list of violations
 * that the {@link io.github.khezyapp.api.exception.controller.CommonExceptionAdviceController}
 * can map to field-level errors.
 * </p>
 */
@Getter
public class InputValidationException extends RestApiException {
    /**
     * The fixed title used for all input validation failures.
     */
    public static final String TITLE = "Invalid Input";

    /**
     * The set of specific constraint violations discovered during validation.
     */
    private final Set<ConstraintViolation<Object>> violations;

    /**
     * Constructs a new {@code InputValidationException} with the provided violations.
     * <p>
     * Automatically sets the message key to {@link ExceptionMessages#VALIDATION_ERROR},
     * includes the count of violations as a message argument, and assigns the
     * business error code {@link ErrorMessageCode#INPUT_VALIDATION_ERROR}.
     * </p>
     *
     * @param violations the set of {@link ConstraintViolation} objects found; must not be null
     */
    public InputValidationException(final Set<ConstraintViolation<Object>> violations) {
        super(ExceptionMessages.VALIDATION_ERROR, TITLE, HttpStatus.BAD_REQUEST, null,
                ErrorMessageCode.INPUT_VALIDATION_ERROR.name(), new Object[] {violations.size()}, null, null);
        this.violations = violations;
    }
}
