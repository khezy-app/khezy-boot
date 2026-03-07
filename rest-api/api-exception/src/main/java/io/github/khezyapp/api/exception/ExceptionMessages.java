package io.github.khezyapp.api.exception;

public final class ExceptionMessages {

    private ExceptionMessages() {
    }

    public static final String PARSE_JSON_ERROR = "io.github.khezyapp.api.exception.parse_json_error";
    public static final String INVALID_JSON_FORMAT = "io.github.khezyapp.api.exception.invalid_json_format";
    public static final String MISSING_REQUEST_BODY = "io.github.khezyapp.api.exception.missing_request_body";
    public static final String INTERNAL_SERVER_ERROR = "io.github.khezyapp.api.exception.internal_server_error";
    public static final String VALIDATION_ERROR = "io.github.khezyapp.api.exception.validation_error";
    public static final String UNAUTHORIZED_ERROR = "io.github.khezyapp.api.exception.unauthorized_error";
    public static final String FORBIDDEN_ERROR = "io.github.khezyapp.api.exception.forbidden_error";
    public static final String TOKEN_EXPIRED = "io.github.khezyapp.api.exception.token_expired";
}
