package io.github.khezyapp.api.security.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ProblemDetail;

/**
 * JSON response body returned when a REST API request is denied. Extends
 * {@link ProblemDetail} with optional fields that signal whether additional
 * multi-factor authentication is required and which method should be used.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AccessDeniedResponse extends ProblemDetail {
    private Boolean requiredMFA;
    private String mfaMethod;
}
