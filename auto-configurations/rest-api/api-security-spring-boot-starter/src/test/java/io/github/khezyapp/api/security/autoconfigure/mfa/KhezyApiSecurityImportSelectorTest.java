package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.autoconfigure.KhezySecurityAutoConfiguration;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import io.github.khezyapp.api.security.authz.RequiredFactorAuthorityAuthorization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyApiSecurityImportSelectorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should import MFA config when mfAuthorities are specified")
    void shouldImportMfaConfigWithAuthorities() {
        this.contextRunner
                .withUserConfiguration(ValidKhezyApiSecurityConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(RequiredFactorAuthorityAuthorization.class);
                });
    }

    @Test
    @DisplayName("Should fall back to AuthenticatedAuthorizationManager when no mfAuthorities")
    void shouldFallbackWithoutAuthorities() {
        this.contextRunner
                .withUserConfiguration(EmptyKhezyApiSecurityConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(AuthorizationManager.class);
                    assertThat(context)
                            .doesNotHaveBean(RequiredFactorAuthorityAuthorization.class);
                });
    }

    @Test
    @DisplayName("Should throw when authority missing FACTOR_ prefix")
    void shouldThrowOnInvalidPrefix() {
        this.contextRunner
                .withUserConfiguration(InvalidKhezyApiSecurityConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().cause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("FACTOR_");
                });
    }

    @EnableKhezyApiSecurity(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_BEARER"})
    @org.springframework.context.annotation.Import(KhezySecurityAutoConfiguration.class)
    static class ValidKhezyApiSecurityConfig {
    }

    @EnableKhezyApiSecurity()
    @org.springframework.context.annotation.Import(KhezySecurityAutoConfiguration.class)
    static class EmptyKhezyApiSecurityConfig {
    }

    @EnableKhezyApiSecurity(mfAuthorities = {"ROLE_USER"})
    @org.springframework.context.annotation.Import(KhezySecurityAutoConfiguration.class)
    static class InvalidKhezyApiSecurityConfig {
    }
}
