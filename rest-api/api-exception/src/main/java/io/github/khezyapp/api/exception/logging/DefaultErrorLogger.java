package io.github.khezyapp.api.exception.logging;

import io.github.khezyapp.api.exception.data.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * Default implementation of {@link ErrorLogger} that utilizes SLF4J for log output.
 * <p>
 * This logger evaluates {@link ErrorLoggingProperties} to decide the logging level.
 * It automatically attempts to extract the {@code traceId} from the response properties
 * or the {@link org.slf4j.MDC} to ensure logs are correlatable across distributed systems.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultErrorLogger implements ErrorLogger {
    private final ErrorLoggingProperties props;

    /**
     * Records the error details to the application logs based on the configured properties.
     * <p>
     * If the response status meets or exceeds the {@code errorThreshold}, an ERROR log
     * is generated with optional stack trace. Otherwise, a WARN log is generated
     * with a concise detail message.
     * </p>
     *
     * @param ex       the intercepted exception
     * @param response the finalized error response
     */
    @Override
    public void log(final Exception ex,
                    final ErrorResponse response) {
        if (!props.isEnabled()) {
            return;
        }

        if (response.getStatus() >= props.getErrorThreshold()) {
            final var traceId = Optional.ofNullable(response.getProperties())
                    .map(res -> res.get("traceId"))
                    .orElse(MDC.get("traceId"));
            if (props.isIncludeStackTrace()) {
                log.error("API Error: {} - TraceId: {}", response.getErrorCode(), traceId, ex);
            } else {
                log.error("API Error: {} - TraceId: {}", response.getErrorCode(), traceId);
            }
        } else {
            log.warn("API Warning: {} - Detail: {}", response.getErrorCode(), ex.getMessage());
        }
    }
}
