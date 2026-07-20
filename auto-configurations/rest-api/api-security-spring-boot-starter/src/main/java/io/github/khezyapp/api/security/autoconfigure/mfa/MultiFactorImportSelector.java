package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableMFA;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;

public class MultiFactorImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(final AnnotationMetadata importingClassMetadata) {
        final var enableMFA = importingClassMetadata.getAnnotationAttributes(EnableMFA.class.getName());
        final var authorities = (String[]) enableMFA.get("mfAuthorities");
        final var invalidAuthorities = new ArrayList<String>();
        for (final String authority : authorities) {
            if (!authority.startsWith(RequiredFactorAuthority.PREFIX)) {
                invalidAuthorities.add(authority);
            }
        }
        if (!invalidAuthorities.isEmpty()) {
            throw new IllegalStateException(
                    "Multiple Factor Authorities must start with prefix "
                            + "'FACTOR_'. (%s)".formatted(invalidAuthorities));
        }
        return new String[] {MultiFactorAuthenticationConfig.class.getName()};
    }
}
