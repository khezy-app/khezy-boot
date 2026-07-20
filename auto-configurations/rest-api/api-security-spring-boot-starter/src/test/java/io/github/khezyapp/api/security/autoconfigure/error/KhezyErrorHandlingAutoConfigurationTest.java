package io.github.khezyapp.api.security.autoconfigure.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.khezyapp.api.security.authn.RestApiAuthenticationEntryPoint;
import io.github.khezyapp.api.security.authz.RestApiAccessDeniedHandler;
import io.github.khezyapp.api.security.autoconfigure.KhezyErrorHandlingAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyErrorHandlingAutoConfigurationTest {

    @Test
    @DisplayName("Should register error handlers when Jackson is available")
    void shouldRegisterHandlers() {
        final var contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KhezyErrorHandlingAutoConfiguration.class))
                .withUserConfiguration(ObjectMapperConfig.class);
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(AccessDeniedHandler.class);
                    assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                    assertThat(context.getBean(AccessDeniedHandler.class))
                            .isInstanceOf(RestApiAccessDeniedHandler.class);
                    assertThat(context.getBean(AuthenticationEntryPoint.class))
                            .isInstanceOf(RestApiAuthenticationEntryPoint.class);
                });
    }

    @Test
    @DisplayName("Should not override user-provided AccessDeniedHandler")
    void shouldNotOverrideCustomAccessDeniedHandler() {
        final var contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KhezyErrorHandlingAutoConfiguration.class))
                .withUserConfiguration(CustomAccessDeniedConfig.class);
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(AccessDeniedHandler.class);
                    assertThat(context.getBean(AccessDeniedHandler.class))
                            .isNotInstanceOf(RestApiAccessDeniedHandler.class);
                });
    }

    @Test
    @DisplayName("Should not override user-provided AuthenticationEntryPoint")
    void shouldNotOverrideCustomAuthEntryPoint() {
        final var contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KhezyErrorHandlingAutoConfiguration.class))
                .withUserConfiguration(CustomAuthEntryPointConfig.class);
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                    assertThat(context.getBean(AuthenticationEntryPoint.class))
                            .isNotInstanceOf(RestApiAuthenticationEntryPoint.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAccessDeniedConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AccessDeniedHandler customAccessDeniedHandler() {
            return (request, response, exception) -> {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAuthEntryPointConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AuthenticationEntryPoint customAuthEntryPoint() {
            return (request, response, exception) -> {
            };
        }
    }
}
