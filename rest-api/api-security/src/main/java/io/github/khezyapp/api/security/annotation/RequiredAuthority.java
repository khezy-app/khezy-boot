package io.github.khezyapp.api.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation that enforces a Spring Security {@code @PreAuthorize} check
 * requiring at least one of the specified authorities.
 * <p>Example usage:</p>
 * <pre>{@code
 * @RequiredAuthority(authorities = {"ADMIN", "MANAGER"})
 * public void deleteUser(String id) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyAuthority({authorities})")
public @interface RequiredAuthority {

    /**
     * The list of authority strings — access is granted if the user holds any of these.
     *
     * @return the required authority names
     */
    String[] authorities();
}
