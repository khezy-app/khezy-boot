package io.github.khezyapp.api.audit.extractor;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link AbstractBodyExtractor} that handles JSON payloads.
 * <p>
 * This extractor specifically targets method arguments annotated with Spring's
 * {@link RequestBody} annotation when the HTTP request content type is
 * {@code application/json}.
 * </p>
 */
public class PayloadBodyExtractor extends AbstractBodyExtractor {

    /**
     * Constructs a new payload extractor with the specified sensitive data masker.
     *
     * @param sensitiveMasker the masker to apply to extracted JSON body values
     */
    public PayloadBodyExtractor(final SensitiveMasker sensitiveMasker) {
        super(sensitiveMasker);
    }

    /**
     * Determines if this extractor supports the given request and method invocation.
     * <p>
     * Support is granted if the request is non-null and the {@code Content-Type}
     * header is compatible with {@link MediaType#APPLICATION_JSON}.
     * </p>
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return {@code true} if the request is a JSON request, {@code false} otherwise
     */
    @Override
    public boolean supports(final MethodInvocation invocation,
                            final HttpServletRequest request) {
        return Objects.nonNull(request) && isContentType(request, MediaType.APPLICATION_JSON);
    }

    /**
     * Extracts the request body from the method arguments.
     * <p>
     * This method iterates through the parameters of the intercepted method to find
     * the one annotated with {@code @RequestBody}. It then retrieves the
     * corresponding argument value from the invocation.
     * </p>
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return a map representation of the request body, or an empty map if no {@code @RequestBody} is found
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> doExtract(final MethodInvocation invocation,
                                            final HttpServletRequest request) {
        final var parameters = invocation.getMethod().getParameters();
        var index = -1;
        for (final var param : parameters) {
            index++;
            final var isRequestBody = AnnotatedElementUtils.hasAnnotation(param, RequestBody.class);
            if (!isRequestBody) {
                continue;
            }
            final var args = invocation.getArguments()[index];
            return (Map<String, Object>) args;
        }
        return Map.of();
    }
}
