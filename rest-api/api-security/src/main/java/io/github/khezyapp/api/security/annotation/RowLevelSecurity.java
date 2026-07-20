package io.github.khezyapp.api.security.annotation;

import java.lang.annotation.*;

/**
 * Annotation used to declaratively apply Row-Level Security filters.
 * Can be used to resolve filter parameters via SpEL or delegate to a
 * {@link io.github.khezyapp.api.security.api.RowLevelSecurityRule}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RowLevelSecurity.List.class)
public @interface RowLevelSecurity {

    /** The name of the @FilterDef defined on the Entity. */
    String filterName();

    /** The parameter name within the Hibernate filter. */
    String parameterName();

    /** SpEL expression to resolve the parameter value. */
    String expression();

    /** Optional SpEL expression to determine if the filter should be active. */
    String condition() default "true";

    /** Optional delegation to a programmatic rule instead of SpEL resolution. */
    String ruleName() default "";

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RowLevelSecurity[] value();
    }
}
