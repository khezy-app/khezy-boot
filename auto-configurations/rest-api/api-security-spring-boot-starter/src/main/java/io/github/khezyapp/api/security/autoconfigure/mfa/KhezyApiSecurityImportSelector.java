package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthority;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.Objects;

/**
 * {@link ImportSelector} that reads {@link EnableKhezyApiSecurity#mfAuthorities()}
 * and conditionally imports {@link MultiFactorAuthenticationConfig} when MFA authorities
 * are specified.
 */
public class KhezyApiSecurityImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(final AnnotationMetadata importingClassMetadata) {
        final var enableKhezyApiSecurity = importingClassMetadata.getAnnotationAttributes(
                EnableKhezyApiSecurity.class.getName()
        );
        if (Objects.isNull(enableKhezyApiSecurity)) {
            return new String[0];
        }
        final var mfAuthorities = (String[]) enableKhezyApiSecurity.get("mfAuthorities");
        if (Objects.isNull(mfAuthorities) || mfAuthorities.length == 0) {
            return new String[0];
        }
        final var invalidAuthorities = new ArrayList<String>();
        for (final String authority : mfAuthorities) {
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
