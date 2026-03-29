package io.github.khezyapp.api.audit.extractor;

import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Composite implementation of {@link AbstractBodyExtractor} that delegates data extraction
 * to a collection of specialized extractors.
 * <p>
 * This class implements the Strategy pattern, iterating through a list of available
 * extractors (such as JSON, Multipart, or Form extractors) and selecting the first one
 * that supports the current request context.
 * </p>
 */
public class CompositeBodyExtractor {

    /**
     * The list of registered extractors used to process the request body.
     * <p>
     * Typically, these are ordered by specificity or priority to ensure the correct
     * extraction logic is applied to the HTTP request.
     * </p>
     */
    private final List<AbstractBodyExtractor> abstractBodyExtractors;

    public CompositeBodyExtractor(final List<AbstractBodyExtractor> abstractBodyExtractors) {
        Assert.notEmpty(abstractBodyExtractors, "abstractBodyExtractors must not be empty");
        this.abstractBodyExtractors = abstractBodyExtractors.stream()
                .sorted(Comparator.comparing(AbstractBodyExtractor::getOrder))
                .toList();
    }

    /**
     * Iterates through the registered extractors to find one that supports the given
     * invocation and request.
     * <p>
     * The method returns the masked data map from the first matching extractor.
     * If no extractor supports the current request (e.g., an unsupported Media Type
     * or a GET request with no body), it returns {@code null}.
     * </p>
     *
     * @param invocation the current method invocation
     * @param request the current HTTP servlet request
     * @return a map of extracted and masked data, or {@code null} if no suitable extractor is found
     */
    public Map<String, Object> extract(final MethodInvocation invocation,
                                       final HttpServletRequest request) {
        for (final var extractor : abstractBodyExtractors) {
            if (extractor.supports(invocation, request)) {
                return extractor.extract(invocation, request);
            }
        }
        return null;
    }
}
