package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.expression.KhezyMethodSecurityExpressionHandler;
import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import io.github.khezyapp.api.security.token.factory.AuthenticationBuilderFactory;
import io.github.khezyapp.api.security.token.factory.UsernamePasswordAuthenticationBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyApiSecurityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should create AuthenticationBuilderManager with empty factories")
    void shouldCreateManagerWithEmptyFactories() {
        this.contextRunner.run(context -> {
            final var manager = context.getBean(AuthenticationBuilderManager.class);
            assertThat(manager).isNotNull();
        });
    }

    @Test
    @DisplayName("Should not override user-provided AuthenticationBuilderManager")
    void shouldNotOverrideUserManager() {
        this.contextRunner
                .withUserConfiguration(UserManagerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationBuilderManager.class);
                    assertThat(context.getBean(AuthenticationBuilderManager.class))
                            .isInstanceOf(CustomAuthenticationBuilderManager.class);
                });
    }

    @Test
    @DisplayName("Should not override user-provided KhezyMethodSecurityExpressionHandler")
    void shouldNotOverrideUserExpressionHandler() {
        this.contextRunner
                .withUserConfiguration(UserExpressionHandlerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(KhezyMethodSecurityExpressionHandler.class);
                    assertThat(context.getBean(KhezyMethodSecurityExpressionHandler.class))
                            .isSameAs(UserExpressionHandlerConfig.CUSTOM_HANDLER);
                });
    }

    @Test
    @DisplayName("Should not override user-provided AnnotationTemplateExpressionDefaults")
    void shouldNotOverrideUserTemplateDefaults() {
        this.contextRunner
                .withUserConfiguration(UserTemplateDefaultsConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AnnotationTemplateExpressionDefaults.class);
                    assertThat(context.getBean(AnnotationTemplateExpressionDefaults.class))
                            .isSameAs(UserTemplateDefaultsConfig.CUSTOM_DEFAULTS);
                });
    }

    @Test
    @DisplayName("Should collect AuthenticationBuilderFactory beans into manager")
    void shouldCollectFactoryBeans() {
        this.contextRunner
                .withUserConfiguration(UserFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationBuilderManager.class);
                    assertThat(context.getBean(AuthenticationBuilderManager.class).getFactories())
                            .hasSize(9);
                });
    }

    @Test
    @DisplayName("Should create CORS source with bound properties")
    void shouldCreateCorsSourceWithProperties() {
        this.contextRunner
                .withPropertyValues(
                        "khezy.api.cors.enabled=true",
                        "khezy.api.cors.allowed-origins=https://example.com",
                        "khezy.api.cors.path-pattern=/api/**"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UrlBasedCorsConfigurationSource.class);
                    final var source = context.getBean(UrlBasedCorsConfigurationSource.class);
                    assertThat(source).isNotNull();
                });
    }

    // --- User override configurations for testing @ConditionalOnMissingBean ---

    @Configuration(proxyBeanMethods = false)
    static class UserManagerConfig {
        @Bean
        AuthenticationBuilderManager customManager() {
            return new CustomAuthenticationBuilderManager();
        }
    }

    static class CustomAuthenticationBuilderManager extends AuthenticationBuilderManager {
        CustomAuthenticationBuilderManager() {
            super(java.util.List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserExpressionHandlerConfig {
        static final KhezyMethodSecurityExpressionHandler CUSTOM_HANDLER =
                new KhezyMethodSecurityExpressionHandler(
                        java.util.List.of(),
                        new io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry(
                                java.util.List.of()));

        @Bean
        KhezyMethodSecurityExpressionHandler customHandler() {
            return CUSTOM_HANDLER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserTemplateDefaultsConfig {
        static final AnnotationTemplateExpressionDefaults CUSTOM_DEFAULTS =
                new AnnotationTemplateExpressionDefaults();

        @Bean
        AnnotationTemplateExpressionDefaults customDefaults() {
            return CUSTOM_DEFAULTS;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserFactoryConfig {
        @Bean
        AuthenticationBuilderFactory customFactory() {
            return new UsernamePasswordAuthenticationBuilderFactory();
        }
    }
}
