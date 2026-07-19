package io.github.khezyapp.api.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/**
 * Meta-annotation for custom business rule authorization.
 * Bridges declarative method security with
 * {@link io.github.khezyapp.api.security.api.AuthorizationRule} implementations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@PreAuthorize(value = "check('{ruleName}', {params})")
public @interface RequiredAuthorizationRule {

    String ruleName();

    String params();
}
