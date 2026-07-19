package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import io.github.khezyapp.api.security.authz.RequiredFactorAuthorityAuthorization;
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
        final var enableMFA = importMetadata.getAnnotationAttributes(EnableMFA.class.getName());
        final var mfAuthorities = (String[]) enableMFA.get("mfAuthorities");
        this.authorities = Arrays.asList(mfAuthorities);

        if (log.isDebugEnabled()) {
            log.debug("Enabled multi-factor authentication configuration with authorities: {}", this.authorities);
        }
    }
}
