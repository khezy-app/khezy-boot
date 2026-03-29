package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import org.springframework.util.Assert;

import java.util.*;

/**
 * A builder class for constructing and configuring a {@link SensitiveMasker} instance.
 * <p>
 * This class allows for the registration of custom {@link SensitiveMaskerStrategy}
 * implementations. If no strategies are provided, it defaults to a standard
 * configuration supporting collections, maps, and Java Beans.
 * </p>
 */
public class SensitiveMaskerBuilder {
    private final Map<Class<?>, SensitiveMaskerStrategy> sensitiveMaskerStrategies = new LinkedHashMap<>();

    private SensitiveMaskerBuilder() {
    }

    /**
     * Creates a new instance of the {@code SensitiveMaskerBuilder}.
     *
     * @return a new builder instance
     */
    public static SensitiveMaskerBuilder builder() {
        return new SensitiveMaskerBuilder();
    }

    /**
     * Registers one or more custom masking strategies into the builder.
     * <p>
     * Strategies are stored in a {@link LinkedHashMap} to maintain registration
     * order, ensuring predictable evaluation in the resulting composite strategy.
     * </p>
     *
     * @param sensitiveMaskerStrategy the strategies to register
     * @return the current builder instance for method chaining
     * @throws IllegalArgumentException if the strategies array contains null elements
     */
    public SensitiveMaskerBuilder registerStrategy(final SensitiveMaskerStrategy... sensitiveMaskerStrategy) {
        Assert.noNullElements(sensitiveMaskerStrategy, "sensitiveMaskerStrategy must not contain null elements");
        for (final var strategy : sensitiveMaskerStrategy) {
            sensitiveMaskerStrategies.put(strategy.getClass(), strategy);
        }
        return this;
    }

    /**
     * Finalizes the configuration and creates a {@link SensitiveMasker} instance.
     * <p>
     * If no strategies were explicitly registered, this method populates the
     * masker with default implementations for collections, maps, and beans.
     * The strategies are then encapsulated within a
     * {@link CompositeSensitiveMaskerStrategy} for execution.
     * </p>
     *
     * @return a fully configured {@link SensitiveMasker} implementation
     */
    public SensitiveMasker build() {
        final var defaultMaskerStrategies = new SensitiveMaskerStrategy[] {
                new CollectionSensitiveMaskerStrategy(),
                new MapSensitiveMaskerStrategy(),
                new BeanSensitiveMaskerStrategy()
        };
        if (sensitiveMaskerStrategies.isEmpty()) {
            registerStrategy(defaultMaskerStrategies);
        } else {
            for (final var defaultMaskerStrategy : sensitiveMaskerStrategies.values()) {
                if (!sensitiveMaskerStrategies.containsKey(defaultMaskerStrategy.getClass())) {
                    sensitiveMaskerStrategies.put(defaultMaskerStrategy.getClass(), defaultMaskerStrategy);
                }
            }
        }
        return new DefaultSensitiveMasker(new CompositeSensitiveMaskerStrategy(
                new ArrayList<>(this.sensitiveMaskerStrategies.values())
        ));
    }
}
