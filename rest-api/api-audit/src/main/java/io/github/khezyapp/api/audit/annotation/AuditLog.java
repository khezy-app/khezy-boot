package io.github.khezyapp.api.audit.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation used to mark methods or types for audit logging.
 * <p>
 * When applied to a method, it indicates that the execution should be captured
 * as an audit event. It allows for the specification of the action name,
 * target entity class, and the identifier of the entity being modified.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuditLog {

    /**
     * Alias for {@link #action()}.
     * * @return the action name
     */
    @AliasFor("action")
    String value() default "";

    /**
     * The name of the action being performed (e.g., "CREATE_USER", "UPDATE_ORDER").
     * * @return the action name
     */
    @AliasFor("value")
    String action() default "";

    /**
     * The class of the entity associated with this audit entry.
     * * @return the entity class, defaults to {@code void.class} if not specified
     */
    Class<?> entityClass() default void.class;

    /**
     * A SpEL (Spring Expression Language) expression used to extract the
     * entity identifier from the method arguments or context.
     * * @return the entity ID expression
     */
    String entityId() default "";

    /**
     * If set to {@code true}, the annotated method or type will be excluded
     * from audit tracking.
     * * @return {@code true} if audit logging should be ignored, {@code false} otherwise
     */
    boolean ignore() default false;
}
