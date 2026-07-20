package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.token.factory.AuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.BearerTokenAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.CASTokenAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.JwtTokenAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.OAuth2LonginAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.OneTimeTokenAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.Saml2AuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.UsernamePasswordAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.WebAuthnAuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.X509AuthenticationBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationBuilderFactoryConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should register UsernamePasswordAuthenticationBuilderFactory")
    void shouldRegisterUsernamePasswordFactory() {
        this.contextRunner.run(context -> {
            assertThat(context)
                    .hasSingleBean(UsernamePasswordAuthenticationBuilderFactory.class);
        });
    }

    @Test
    @DisplayName("Should register all conditional factory beans")
    void shouldRegisterAllFactories() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(
                    BearerTokenAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    CASTokenAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    JwtTokenAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    OAuth2LonginAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    OneTimeTokenAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    Saml2AuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    UsernamePasswordAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    WebAuthnAuthenticationBuilderFactory.class);
            assertThat(context).hasSingleBean(
                    X509AuthenticationBuilderFactory.class);
        });
    }

    @Test
    @DisplayName("Should collect all factories into AuthenticationBuilderManager")
    void shouldCollectFactoriesIntoManager() {
        this.contextRunner.run(context -> {
            final var providers = context
                    .getBeansOfType(AuthenticationBuilderFactory.class);
            assertThat(providers).hasSize(9);
        });
    }

    @Test
    @DisplayName("Should not override user-provided factory of same type")
    void shouldNotOverrideUserFactory() {
        this.contextRunner
                .withUserConfiguration(CustomFactoryConfig.class)
                .run(context -> {
                    final var factories = context
                            .getBeansOfType(AuthenticationBuilderFactory.class);
                    assertThat(factories).hasSize(9);
                    assertThat(context).hasBean("customUsernamePasswordFactory");
                    assertThat(context).hasSingleBean(UsernamePasswordAuthenticationBuilderFactory.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFactoryConfig {
        @Bean
        UsernamePasswordAuthenticationBuilderFactory customUsernamePasswordFactory() {
            return new UsernamePasswordAuthenticationBuilderFactory();
        }
    }
}
