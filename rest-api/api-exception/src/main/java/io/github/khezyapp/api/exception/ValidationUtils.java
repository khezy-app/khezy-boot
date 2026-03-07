package io.github.khezyapp.api.exception;

import io.github.khezyapp.api.exception.custom.InputValidationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ValidationUtils {
    private final Validator validator;

    public void throwIfInvalid(final Object dto,
                               final Class<?>... groups) {
        final var errors = validator.validate(dto, groups);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }
    }
}
