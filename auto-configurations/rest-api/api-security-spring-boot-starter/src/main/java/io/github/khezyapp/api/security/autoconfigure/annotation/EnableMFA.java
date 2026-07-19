package io.github.khezyapp.api.security.autoconfigure.annotation;

import io.github.khezyapp.api.security.autoconfigure.mfa.MultiFactorImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MultiFactorImportSelector.class)
public @interface EnableMFA {

    String[] mfAuthorities() default {};

}
