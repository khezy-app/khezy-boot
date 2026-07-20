package io.github.khezyapp.api.security.autoconfigure.annotation;

import io.github.khezyapp.api.security.autoconfigure.mfa.KhezyApiSecurityImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation that enables KHEZY API security with method-level
 * security and optional multi-factor authentication (MFA).
 * <p>
 * Equivalent to combining {@link EnableMethodSecurity} with {@code @EnableMFA}.
 * <p>
 * Usage:
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}EnableKhezyApiSecurity(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_SECRET_QUESTION"})
 * public class MyApp { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableMethodSecurity
@Import(KhezyApiSecurityImportSelector.class)
public @interface EnableKhezyApiSecurity {

    /**
     * MFA factor authorities that are globally required.
     * Each authority must start with {@code FACTOR_}.
     * <p>
     * When non-empty, enables MFA with the specified factor authorities.
     */
    String[] mfAuthorities() default {};
}
