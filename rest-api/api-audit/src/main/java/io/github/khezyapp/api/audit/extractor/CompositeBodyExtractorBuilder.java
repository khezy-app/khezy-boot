package io.github.khezyapp.api.audit.extractor;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder class for creating and configuring a {@link CompositeBodyExtractor}.
 * <p>
 * This builder allows for the manual registration of custom {@link AbstractBodyExtractor}
 * implementations and ensures that a standard set of default extractors (Form, Multipart,
 * and Payload) are included if not already provided. It also enforces the requirement
 * for a {@link SensitiveMasker} to be present for data sanitization.
 * </p>
 */
public class CompositeBodyExtractorBuilder {
    /**
     * Internal registry of extractors, keyed by their implementation class to prevent duplicates.
     */
    private final Map<Class<?>, AbstractBodyExtractor> extractors = new HashMap<>();

    /**
     * The masker to be applied to all extracted data.
     */
    private SensitiveMasker sensitiveMasker;

    /**
     * Private constructor to enforce the use of the static {@link #builder()} method.
     */
    private CompositeBodyExtractorBuilder() {
    }

    /**
     * Initializes a new instance of the builder.
     *
     * @return a new {@link CompositeBodyExtractorBuilder}
     */
    public static CompositeBodyExtractorBuilder builder() {
        return new CompositeBodyExtractorBuilder();
    }

    /**
     * Registers one or more custom extractors into the builder.
     * <p>
     * If an extractor of the same class is already registered, it will be overwritten
     * by the new instance.
     * </p>
     *
     * @param extractors the extractor instances to add
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if the array contains null elements
     */
    public CompositeBodyExtractorBuilder registerExtractor(final AbstractBodyExtractor... extractors) {
        Assert.noNullElements(extractors, "extractors must not contain null elements");
        for (final var extractor : extractors) {
            this.extractors.put(extractor.getClass(), extractor);
        }
        return this;
    }

    /**
     * Configures the sensitive data masker for the resulting extractor.
     *
     * @param sensitiveMasker the masker to use
     * @return this builder instance for method chaining
     */
    public CompositeBodyExtractorBuilder sensitiveMasker(final SensitiveMasker sensitiveMasker) {
        this.sensitiveMasker = sensitiveMasker;
        return this;
    }

    /**
     * Constructs the {@link CompositeBodyExtractor} with the configured settings.
     * <p>
     * The build process:
     * <ol>
     * <li>Validates that a {@link SensitiveMasker} has been provided.</li>
     * <li>Appends default extractors (Form, Multipart, Payload) if they haven't
     * been explicitly overridden by {@link #registerExtractor}.</li>
     * </ol>
     * </p>
     *
     * @return a fully initialized {@link CompositeBodyExtractor}
     * @throws NullPointerException if the sensitiveMasker is null
     */
    public CompositeBodyExtractor build() {
        Objects.requireNonNull(sensitiveMasker, "sensitiveMasker must not be null");
        final var defaultExtractors = new AbstractBodyExtractor[] {
                new FormBodyExtractor(sensitiveMasker),
                new MultiPartBodyExtractor(sensitiveMasker),
                new PayloadBodyExtractor(sensitiveMasker)
        };
        if (extractors.isEmpty()) {
            for (final var defaultExtractor : defaultExtractors) {
                extractors.put(defaultExtractor.getClass(), defaultExtractor);
            }
        } else {
            for (final var defaultExtractor : defaultExtractors) {
                if (!extractors.containsKey(defaultExtractor.getClass())) {
                    extractors.put(defaultExtractor.getClass(), defaultExtractor);
                }
            }
        }
        final var extractors = this.extractors.values()
                .stream()
                .toList();
        return new CompositeBodyExtractor(extractors);
    }
}
