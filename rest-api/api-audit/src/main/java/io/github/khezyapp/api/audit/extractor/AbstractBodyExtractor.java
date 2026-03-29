package io.github.khezyapp.api.audit.extractor;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Base abstract class for extracting and masking request body data from
 * {@link MethodInvocation} and {@link HttpServletRequest}.
 * <p>
 * This class defines a template for content-type specific extractors,
 * ensuring that all extracted data is passed through a {@link SensitiveMasker}
 * before being returned as audit metadata.
 * </p>
 */
public abstract class AbstractBodyExtractor {

    /**
     * The masker used to sanitize sensitive information within the extracted data.
     */
    protected final SensitiveMasker sensitiveMasker;

    /**
     * Constructs a new extractor with the specified sensitive data masker.
     * * @param sensitiveMasker the masker to apply to extracted values
     */
    public AbstractBodyExtractor(final SensitiveMasker sensitiveMasker) {
        this.sensitiveMasker = sensitiveMasker;
    }

    /**
     * Determines if this extractor can handle the given request and method invocation.
     * * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return {@code true} if this extractor supports the request, {@code false} otherwise
     */
    public abstract boolean supports(MethodInvocation invocation,
                                     HttpServletRequest request);

    /**
     * Template method that performs the extraction and applies sensitive data masking.
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return a masked map of extracted property names and their values
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(final MethodInvocation invocation,
                                       final HttpServletRequest request) {
        final var extractedValue = doExtract(invocation, request);
        return (Map<String, Object>) sensitiveMasker.mask(extractedValue);
    }

    /**
     * Internal implementation for extracting raw data from the request or invocation.
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return a raw map of extracted data
     */
    protected abstract Map<String, Object> doExtract(MethodInvocation invocation,
                                                     HttpServletRequest request);

    /**
     * Returns the execution order of this extractor.
     * <p>
     * Lower values typically indicate higher priority when multiple extractors are available.
     * </p>
     * * @return the order value, defaults to {@code 0}
     */
    public int getOrder() {
        return 0;
    }

    /**
     * Utility method to check if the request's Content-Type matches or includes the expected {@link MediaType}.
     *
     * @param request the current HTTP servlet request
     * @param expectedMediaType the media type to check against
     * @return {@code true} if the media types are compatible, {@code false} otherwise
     */
    public boolean isContentType(final HttpServletRequest request,
                                 final MediaType expectedMediaType) {
        try {
            final var contentType = request.getContentType();
            final var mediaType = MediaType.parseMediaType(contentType);
            return mediaType.includes(expectedMediaType);
        } catch (final Exception e) {
            return false;
        }
    }
}
