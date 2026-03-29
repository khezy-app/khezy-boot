package io.github.khezyapp.api.audit.extractor;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link AbstractBodyExtractor} that handles multipart form-data requests.
 * <p>
 * This extractor processes requests with the {@code multipart/form-data} content type,
 * capturing both standard form parameters and the filenames of uploaded files.
 * It ensures that temporary resources created during multipart resolution are
 * properly cleaned up after extraction.
 * </p>
 */
public class MultiPartBodyExtractor extends AbstractBodyExtractor {

    /**
     * Constructs a new multipart body extractor with the specified sensitive data masker.
     *
     * @param sensitiveMasker the masker to apply to extracted form parameter values
     */
    public MultiPartBodyExtractor(final SensitiveMasker sensitiveMasker) {
        super(sensitiveMasker);
    }

    /**
     * Determines if this extractor supports the given request and method invocation.
     * <p>
     * Support is granted if the request is non-null and the {@code Content-Type}
     * header is compatible with {@link MediaType#MULTIPART_FORM_DATA}.
     * </p>
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return {@code true} if the request is a multipart request, {@code false} otherwise
     */
    @Override
    public boolean supports(final MethodInvocation invocation,
                            final HttpServletRequest request) {
        return Objects.nonNull(request) && isContentType(request, MediaType.MULTIPART_FORM_DATA);
    }

    /**
     * Extracts parameters and file metadata from the multipart request.
     * <p>
     * The extraction process:
     * <ol>
     * <li>Resolves the request using {@link StandardServletMultipartResolver}.</li>
     * <li>Collects all standard form parameters (handling single and multiple value arrays).</li>
     * <li>Collects the original filenames of uploaded files (omitting binary content for the log).</li>
     * <li>Guarantees resource cleanup via {@code finally} block to prevent temporary file leaks.</li>
     * </ol>
     * </p>
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return a map containing form parameters and filenames, or {@code null} if the request is not multipart
     */
    @Override
    protected Map<String, Object> doExtract(final MethodInvocation invocation,
                                            final HttpServletRequest request) {
        final var resolver = new StandardServletMultipartResolver();
        if (!resolver.isMultipart(request)) {
            return null;
        }

        MultipartHttpServletRequest multipartRequest = null;
        try {
            multipartRequest = resolver.resolveMultipart(request);

            final var body = new LinkedHashMap<String, Object>();

            // Extract Parameters
            multipartRequest.getParameterMap().forEach((name, values) -> {
                if (values.length == 1) {
                    body.put(name, values[0]);
                } else {
                    body.put(name, values);
                }
            });

            // Extract File Names
            multipartRequest.getFileMap().forEach((name, file) -> {
                body.put(name, file.getOriginalFilename());
            });

            return body;
            // 4. IMPORTANT: Clean up temporary files created by the resolver

        } finally {
            if (multipartRequest != null) {
                resolver.cleanupMultipart(multipartRequest);
            }
        }
    }
}
