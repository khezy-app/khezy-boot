package io.github.khezyapp.api.audit;

import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.ConcurrentLruCache;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Utility class for managing Hibernate entity state reflection and instantiation.
 * <p>
 * This class provides mechanisms to manually reconstruct entity instances from
 * raw Hibernate state arrays, primarily used during interceptor cycles to create
 * "snapshots" of entities for comparison. It utilizes a {@link ConcurrentLruCache}
 * to store the identity property name of classes, minimizing reflection overhead.
 * </p>
 */
public final class HibernateUtils {

    private HibernateUtils() {
    }

    /**
     * An LRU cache that maps entity classes to their primary key property names.
     * <p>
     * This prevents redundant scanning of class fields for the {@code @Id} annotation
     * across multiple audit events.
     * </p>
     */
    private static final ConcurrentLruCache<Class<?>, String> CACHE = new ConcurrentLruCache<>(
            250,
            HibernateUtils::extractIdProperty
    );

    /**
     * Instantiates an entity and populates it with the provided identity and state.
     * <p>
     * This method uses Spring's {@link PropertyAccessorFactory} to safely set values
     * into the entity's fields based on the property names provided by the Hibernate
     * interceptor. It ensures that the ID property is set first before filling
     * in the remaining state.
     * </p>
     *
     * @param <T>           the type of the entity
     * @param clazz         the class of the entity to instantiate
     * @param id            the primary key value
     * @param state         the array of field values (Hibernate state)
     * @param propertyNames the array of corresponding field names
     * @return a fully populated instance of the entity
     * @throws RuntimeException if instantiation or property access fails
     */
    public static <T> T populate(final Class<T> clazz,
                                 final Object id,
                                 final Object[] state,
                                 final String[] propertyNames) {
        try {
            final var target = clazz.getDeclaredConstructor().newInstance();
            final var wrapper = PropertyAccessorFactory.forBeanPropertyAccess(target);

            final var idName = CACHE.get(clazz);
            if (wrapper.isWritableProperty(idName)) {
                wrapper.setPropertyValue(idName, id);
            }

            for (int i = 0; i < propertyNames.length; i++) {
                if (wrapper.isWritableProperty(propertyNames[i])) {
                    wrapper.setPropertyValue(propertyNames[i], state[i]);
                }
            }
            return target;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Inspects a class to determine which field is marked as the JPA {@link jakarta.persistence.Id}.
     * <p>
     * If no field is explicitly annotated with {@code @Id}, the method defaults to "id".
     * </p>
     *
     * @param clazz the class to inspect
     * @return the name of the identity property
     */
    public static String extractIdProperty(final Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(jakarta.persistence.Id.class))
                .map(Field::getName)
                .findFirst()
                .orElse("id");
    }
}
