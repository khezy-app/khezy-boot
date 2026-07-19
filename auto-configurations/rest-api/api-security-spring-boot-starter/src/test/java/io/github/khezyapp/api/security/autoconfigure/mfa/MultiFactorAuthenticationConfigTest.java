package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.autoconfigure.KhezySecurityAutoConfiguration;
import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableMFA;
import io.github.khezyapp.api.security.authz.RequiredFactorAuthorityAuthorization;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MultiFactorAuthenticationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should create authorization bean with configured authorities")
    void shouldCreateAuthorizationWithAuthorities() {
        this.contextRunner
                .withUserConfiguration(MfaWithRepositoryConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(RequiredFactorAuthorityAuthorization.class);

                    @SuppressWarnings("unchecked")
                    final var auth = (RequiredFactorAuthorityAuthorization<RequestAuthorizationContext>)
                            context.getBean(RequiredFactorAuthorityAuthorization.class);
                    assertThat(auth).isNotNull();
                });
    }

    @Test
    @DisplayName("Should create authorization bean even without RequiredFactorAuthoritiesRepository")
    void shouldCreateWithoutRepository() {
        this.contextRunner
                .withUserConfiguration(MfaWithoutRepositoryConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(RequiredFactorAuthorityAuthorization.class);
                });
    }

    // --- Test configurations ---

    @EnableMFA(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_BEARER"})
    @Import(KhezySecurityAutoConfiguration.class)
    @Configuration(proxyBeanMethods = false)
    static class MfaWithRepositoryConfig {
        @Bean
        RequiredFactorAuthoritiesRepository factorRepository() {
            return username -> List.of("FACTOR_PASSWORD", "FACTOR_BEARER");
        }
    }

    @EnableMFA(mfAuthorities = {"FACTOR_PASSWORD"})
    @Import(KhezySecurityAutoConfiguration.class)
    static class MfaWithoutRepositoryConfig {

    }
}
