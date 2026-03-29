package io.github.khezyapp.api.audit.javers;

import jakarta.persistence.*;
import org.javers.core.JaversBuilder;
import org.javers.core.metamodel.clazz.EntityDefinition;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Utility class for registering domain classes with the Javers diff engine.
 * <p>
 * This registry simplifies the configuration of {@link JaversBuilder} by automatically
 * distinguishing between JPA Entities and Value Objects. For entities, it identifies
 * the primary key field and automatically ignores relationship-heavy fields (e.g.,
 * {@code @OneToMany}) to prevent deep-graph recursion and infinite loops during
 * difference calculations.
 * </p>
 */
public final class JaversClassRegistry {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JaversClassRegistry() {
    }

    /**
     * Registers a class with the provided Javers builder based on its JPA annotations.
     * <p>
     * The registration logic follows these rules:
     * <ul>
     * <li><b>Entities:</b> If a field annotated with {@link jakarta.persistence.Id} is found,
     * the class is registered as an entity. Any fields marked as JPA relationships
     * (OneToOne, OneToMany, etc.) are added to the ignored properties list to maintain
     * a flat, high-performance audit trail.</li>
     * <li><b>Value Objects:</b> If no ID field is present, the class is registered
     * as a {@code ValueObject}.</li>
     * </ul>
     * </p>
     *
     * @param builder the Javers builder to register the class with
     * @param entityClass the class to be analyzed and registered
     */
    public static void register(final JaversBuilder builder,
                                final Class<?> entityClass) {
        final var idField = Arrays.stream(entityClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Id.class))
                .findFirst();
        if (idField.isPresent()) {
            final var ignoreProperties = Arrays.stream(entityClass.getDeclaredFields())
                    .filter(JaversClassRegistry::isRelationship)
                    .map(Field::getName)
                    .toList();
            builder.registerEntity(new EntityDefinition(entityClass, idField.get().getName(), ignoreProperties));
        } else {
            builder.registerValueObject(entityClass);
        }
    }

    /**
     * Checks if a field is annotated with standard JPA relationship annotations.
     *
     * @param field the field to inspect
     * @return {@code true} if the field represents a database relationship, {@code false} otherwise
     */
    private static boolean isRelationship(final Field field) {
        return field.isAnnotationPresent(OneToOne.class) ||
                field.isAnnotationPresent(OneToMany.class) ||
                field.isAnnotationPresent(ManyToOne.class) ||
                field.isAnnotationPresent(ManyToMany.class);
    }
}
