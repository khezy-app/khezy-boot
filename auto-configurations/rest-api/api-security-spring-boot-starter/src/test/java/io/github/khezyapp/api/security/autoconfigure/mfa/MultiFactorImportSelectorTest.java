package io.github.khezyapp.api.security.autoconfigure.mfa;

import io.github.khezyapp.api.security.autoconfigure.KhezySecurityAutoConfiguration;
import io.github.khezyapp.api.security.autoconfigure.annotation.EnableMFA;
import io.github.khezyapp.api.security.authz.RequiredFactorAuthorityAuthorization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class MultiFactorImportSelectorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should import config when all authorities have FACTOR_ prefix")
    void shouldImportConfigWithValidPrefixes() {
        this.contextRunner
                .withUserConfiguration(ValidMfaConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(RequiredFactorAuthorityAuthorization.class);
                });
    }

    @Test
    @DisplayName("Should throw when authority missing FACTOR_ prefix")
    void shouldThrowOnInvalidPrefix() {
        this.contextRunner
                .withUserConfiguration(InvalidMfaConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure().cause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("FACTOR_");
                });
    }

    @Test
    @DisplayName("Should import config with empty authorities list")
    void shouldImportConfigWithEmptyAuthorities() {
        this.contextRunner
                .withUserConfiguration(EmptyMfaConfig.class)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(RequiredFactorAuthorityAuthorization.class);
                });
    }

    // --- Test configurations ---

    @EnableMFA(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_BEARER"})
    @Import(KhezySecurityAutoConfiguration.class)
    static class ValidMfaConfig {
    }

    @EnableMFA(mfAuthorities = {"ROLE_USER"})
    @Import(KhezySecurityAutoConfiguration.class)
    static class InvalidMfaConfig {
    }

    @EnableMFA(mfAuthorities = {})
    @Import(KhezySecurityAutoConfiguration.class)
    static class EmptyMfaConfig {
    }
}
