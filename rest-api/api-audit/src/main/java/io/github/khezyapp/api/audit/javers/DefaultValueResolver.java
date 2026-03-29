package io.github.khezyapp.api.audit.javers;

import io.github.khezyapp.api.audit.CheckTypes;
import io.github.khezyapp.api.audit.api.SensitiveMasker;
import io.github.khezyapp.api.audit.javers.api.ValueResolver;
import lombok.RequiredArgsConstructor;

/**
 * Default implementation of the {@link ValueResolver} interface that integrates
 * with the data-masker.
 * <p>
 * This class uses a {@link SensitiveMasker} to ensure that any resolved value
 * is appropriately masked or redacted before being committed to the audit log.
 * </p>
 */
@RequiredArgsConstructor
public class DefaultValueResolver implements ValueResolver {
    private final SensitiveMasker sensitiveMasker;

    /**
     * Delegates the value to the {@link SensitiveMasker} to apply masking rules.
     *
     * @param value the raw value to be resolved
     * @return the masked or redacted representation of the value
     */
    @Override
    public Object resolve(final Object value) {
        return sensitiveMasker.mask(value);
    }

    /**
     * Determines if the value is a leaf node based on whether it is a
     * primitive or a standard simple type.
     *
     * @param value the value to inspect
     * @return {@code true} if the value is a primitive type; {@code false} otherwise
     */
    @Override
    public boolean isLeaf(final Object value) {
        return CheckTypes.isPrimitive(value);
    }
}
