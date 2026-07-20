package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import io.github.khezyapp.api.security.authz.RequiredFactorAuthorityAuthorization;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableMFA;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Configuration
@Slf4j
public class MultiFactorAuthenticationConfig implements ImportAware {

    private List<String> authorities;

    @Bean
    RequiredFactorAuthorityAuthorization<RequestAuthorizationContext> requiredFactorAuthorityAuthorization(
            final ObjectProvider<RequiredFactorAuthoritiesRepository> factorAuthoritiesRepositoryObjectProvider
    ) {
        final var requiredFactorAuthoritiesRepository = factorAuthoritiesRepositoryObjectProvider.getIfAvailable();
        return new RequiredFactorAuthorityAuthorization<>(
                requiredFactorAuthoritiesRepository,
                authorities
        );
    }

    @Override
    public void setImportMetadata(final AnnotationMetadata importMetadata) {
        final var enableKhezyApiSecurity = importMetadata.getAnnotationAttributes(
                EnableKhezyApiSecurity.class.getName()
        );
        final var enableMFA = importMetadata.getAnnotationAttributes(EnableMFA.class.getName());

        String[] mfAuthorities;
        if (Objects.nonNull(enableKhezyApiSecurity)) {
            mfAuthorities = (String[]) enableKhezyApiSecurity.get("mfAuthorities");
        } else if (Objects.nonNull(enableMFA)) {
            mfAuthorities = (String[]) enableMFA.get("mfAuthorities");
        } else {
            mfAuthorities = new String[0];
        }
        this.authorities = Arrays.asList(mfAuthorities);

        if (log.isDebugEnabled()) {
            log.debug("Enabled multi-factor authentication configuration with authorities: {}", this.authorities);
        }
    }
}
