package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of {@link SensitiveMaskerStrategy} designed to process collections and arrays.
 * <p>
 * This strategy iterates through the elements of a collection or array, recursively
 * applying masking logic to complex objects while preserving primitive values
 * and nulls. It ensures circular reference protection by registering the
 * container in the {@link SensitiveMaskerContext} before processing its contents.
 * </p>
 */
public class CollectionSensitiveMaskerStrategy implements SensitiveMaskerStrategy {

    /**
     * Determines if the payload is either a {@link Collection} or a Java array.
     *
     * @param payload the object to evaluate
     * @return {@code true} if the payload is a collection or array; {@code false} otherwise
     */
    @Override
    public boolean supports(final Object payload) {
        return isCollection(payload) || isArray(payload);
    }

    /**
     * Orchestrates the masking process for the supported container types.
     *
     * @param payload the collection or array instance to mask
     * @param context the current masking context for recursion and visitor tracking
     * @return a new collection or array containing the masked elements
     */
    @Override
    public Object mask(final Object payload,
                       final SensitiveMaskerContext context) {
        if (isCollection(payload)) {
            return maskCollection((Collection<?>) payload, context);
        }
        if (isArray(payload)) {
            return maskArray(payload, context);
        }
        return payload;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }

    /**
     * Processes a {@link Collection} by creating a new {@link ArrayList} and
     * masking its non-primitive elements.
     *
     * @param payload the collection to process
     * @param context the current masking context
     * @return a list containing masked items
     */
    private Object maskCollection(final Collection<?> payload,
                                  final SensitiveMaskerContext context) {
        final var proceedCollection = new ArrayList<>(payload.size());

        context.registerVisited(payload, proceedCollection);

        for (final var item : payload) {
            if (Objects.isNull(item) ||
                    isPrimitive(item.getClass())) {
                proceedCollection.add(item);
            } else {
                final var mask = context.processMask(item);
                proceedCollection.add(mask);
            }
        }

        return proceedCollection;
    }

    /**
     * Processes a Java array by creating a new array instance of the same
     * component type and masking its non-primitive elements.
     *
     * @param payload the array to process
     * @param context the current masking context
     * @return a new array containing masked items
     */
    private Object maskArray(final Object payload,
                             final SensitiveMaskerContext context) {
        final var length = Array.getLength(payload);
        final var proceedArray = Array.newInstance(payload.getClass().getComponentType(), length);

        context.registerVisited(payload, proceedArray);

        for (var idx = 0; idx < length; idx++) {
            final var item = Array.get(payload, idx);
            if (Objects.isNull(item) ||
                    isPrimitive(item.getClass())) {
                Array.set(proceedArray, idx, item);
            } else {
                final var mask = context.processMask(item);
                Array.set(proceedArray, idx, mask);
            }
        }
        return proceedArray;
    }
}
