package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import org.springframework.util.Assert;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A composite implementation of {@link SensitiveMaskerStrategy} that delegates
 * masking tasks to a list of specific strategies.
 * <p>
 * This class acts as the primary entry point for the masking process, coordinating
 * between specialized strategies for beans, collections, and maps. It ensures
 * that primitives and null values are returned as-is while complex objects are
 * routed to the first supporting strategy.
 * </p>
 */
public class CompositeSensitiveMaskerStrategy implements SensitiveMaskerStrategy {
    private final List<SensitiveMaskerStrategy> sensitiveMaskerStrategies;

    public CompositeSensitiveMaskerStrategy(final List<SensitiveMaskerStrategy> sensitiveMaskerStrategies) {
        Assert.notEmpty(sensitiveMaskerStrategies, "sensitiveMaskerStrategies must not be empty");
        Assert.noNullElements(sensitiveMaskerStrategies, "sensitiveMaskerStrategies must not be contain null elements");
        sensitiveMaskerStrategies.forEach(strategy -> {
            if (strategy instanceof CompositeSensitiveMaskerStrategy) {
                throw  new IllegalArgumentException(
                        """
                        sensitiveMaskerStrategies must not contain `CompositeSensitiveMaskerStrategy` elements. \
                        Please remove `CompositeSensitiveMaskerStrategy` elements""");
            }
        });
        this.sensitiveMaskerStrategies = sensitiveMaskerStrategies.stream()
                .sorted(Comparator.comparing(SensitiveMaskerStrategy::getOrder))
                .toList();
    }

    /**
     * Always returns {@code true} as this composite strategy is designed to
     * handle any payload by delegating to its internal strategies.
     *
     * @param payload the object to evaluate
     * @return {@code true}
     */
    @Override
    public boolean supports(final Object payload) {
        return true;
    }

    /**
     * Orchestrates the masking of a payload by iterating through registered strategies.
     * <p>
     * The resolution logic follows these steps:
     * <ul>
     * <li>Returns {@code null} immediately if the payload is null.</li>
     * <li>Returns the payload as-is if it is a primitive or simple type.</li>
     * <li>Iterates through {@code sensitiveMaskerStrategies} and invokes the first
     * strategy that supports the payload type.</li>
     * <li>Returns the original payload if no specialized strategy is found.</li>
     * </ul>
     * </p>
     *
     * @param payload the object instance to be masked
     * @param context the current masking context for recursion and visitor tracking
     * @return the masked representation of the payload or the original payload
     */
    @Override
    public Object mask(final Object payload,
                       final SensitiveMaskerContext context) {
        if (Objects.isNull(payload)) {
            return null;
        }
        if (isPrimitive(payload.getClass())) {
            return payload;
        }

        for (final var sensitiveMasker : sensitiveMaskerStrategies) {
            if (sensitiveMasker.supports(payload)) {
                return sensitiveMasker.mask(payload, context);
            }
        }
        return payload;
    }
}
