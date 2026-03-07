package io.github.khezyapp.api.exception.autoconfigure;

import io.github.khezyapp.api.exception.controller.AuthExceptionAdviceController;
import io.github.khezyapp.api.exception.controller.JJwtExceptionAdviceController;
import io.github.khezyapp.api.exception.data.ErrorResponse;
import io.github.khezyapp.api.exception.logging.DefaultErrorLogger;
import io.github.khezyapp.api.exception.logging.ErrorLogger;
import io.github.khezyapp.api.exception.logging.ErrorLoggingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyExceptionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration.class,
                    KhezyExceptionAutoConfiguration.class));

    @Test
    @DisplayName("Should register core beans when no conditions are restricted")
    void testCoreBeansRegistration() {
        this.contextRunner.run(context -> {
            assertThat(context).hasBean("khezyI18nException");
            assertThat(context).hasSingleBean(ErrorLogger.class);
            assertThat(context).hasSingleBean(DefaultErrorLogger.class);

            // Verify the MessageSource is correctly configured
            final var ms = (MessageSource) context.getBean("khezyI18nException");
            assertThat(ms).isNotNull();
        });
    }

    @Test
    @DisplayName("Should register Security advice when AuthenticationException is on classpath")
    void testSecurityAdviceRegistration() {
        // This test assumes the dependency is present in the test runtime
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthExceptionAdviceController.class);
        });
    }

    @Test
    @DisplayName("Should register JWT advice when JwtException is on classpath")
    void testJwtAdviceRegistration() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JJwtExceptionAdviceController.class);
        });
    }

    @Test
    @DisplayName("Should allow user to override the ErrorLogger bean")
    void testBeanOverriding() {
        this.contextRunner
                .withUserConfiguration(CustomLoggerConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ErrorLogger.class);
                    assertThat(context).doesNotHaveBean(DefaultErrorLogger.class);
                    assertThat(context.getBean(ErrorLogger.class).getClass())
                            .isEqualTo(CustomErrorLogger.class);
                });
    }

    @Test
    @DisplayName("Should respect logging properties from environment")
    void testPropertiesBinding() {
        this.contextRunner
                .withPropertyValues("khezy.error-handling.logging.enabled=false")
                .run(context -> {
                    final var props = context.getBean(ErrorLoggingProperties.class);
                    assertThat(props.isEnabled()).isFalse();
                });
    }

    // --- Mock Classes for Testing ---

    static class CustomLoggerConfig {
        @Bean
        public ErrorLogger customLogger() {
            return new CustomErrorLogger();
        }
    }

    static class CustomErrorLogger implements ErrorLogger {
        @Override
        public void log(final Exception ex, final ErrorResponse errorResponse) {

        }
    }
}
