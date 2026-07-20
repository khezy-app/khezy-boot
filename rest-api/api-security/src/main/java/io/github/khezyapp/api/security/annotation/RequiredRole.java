package io.github.khezyapp.api.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation for role-based access control.
 * Simplifies standard {@code @PreAuthorize("hasAnyRole(...)")} syntax.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole({roles})")
public @interface RequiredRole {

    /**
     * The list of required roles.
     * <p>Each value must be enclosed in single quotes (e.g., {@code "'ADMIN'"}).
     * The "ROLE_" prefix is automatically applied by Spring Security.</p>
     *
     * @return Array of quoted role strings
     */
    String[] roles();
}
