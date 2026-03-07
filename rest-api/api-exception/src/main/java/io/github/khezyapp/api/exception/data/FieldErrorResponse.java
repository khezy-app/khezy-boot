package io.github.khezyapp.api.exception.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a specific validation failure on a particular field of a request object.
 * <p>
 * This class captures granular details about why a piece of data was rejected,
 * including the field name, the reason for rejection, the invalid value provided,
 * and the original object's name.
 * </p>
 */
@Getter
@Setter
public class FieldErrorResponse {
    /**
     * The name of the field that failed validation (e.g., "email" or "address.zipCode").
     */
    private String field;

    /**
     * A human-readable message explaining why the validation failed.
     */
    private String reason;

    /**
     * The actual value that was submitted and rejected.
     */
    private Object rejectedValue;

    /**
     * The name of the target object being validated (e.g., "userDTO").
     */
    private String objectName;

    /**
     * The validation constraint code (e.g., "NotBlank" or "Size").
     */
    private String code;
}
