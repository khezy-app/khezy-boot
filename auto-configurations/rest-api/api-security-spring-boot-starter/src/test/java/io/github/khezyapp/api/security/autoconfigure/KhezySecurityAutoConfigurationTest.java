package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.autoconfigure.properties.KhezyCorsProperties;
import io.github.khezyapp.api.security.expression.KhezyMethodSecurityExpressionHandler;
import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class KhezySecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should register AuthenticationBuilderManager when no user bean exists")
    void shouldRegisterAuthenticationBuilderManager() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthenticationBuilderManager.class);
        });
    }

    @Test
    @DisplayName("Should register AnnotationTemplateExpressionDefaults")
    void shouldRegisterAnnotationTemplateExpressionDefaults() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AnnotationTemplateExpressionDefaults.class);
        });
    }

    @Test
    @DisplayName("Should register KhezyMethodSecurityExpressionHandler")
    void shouldRegisterMethodSecurityExpressionHandler() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KhezyMethodSecurityExpressionHandler.class);
        });
    }

    @Test
    @DisplayName("Should not register CORS source when property is not set")
    void shouldNotRegisterCorsSourceByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(UrlBasedCorsConfigurationSource.class);
        });
    }

    @Test
    @DisplayName("Should register CORS source when khezy.api.cors.enabled is true")
    void shouldRegisterCorsSourceWhenEnabled() {
        this.contextRunner
                .withPropertyValues(
                        "khezy.api.cors.enabled=true",
                        "khezy.api.cors.allowed-origins=https://app.example.com",
                        "khezy.api.cors.path-pattern=/api/**"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UrlBasedCorsConfigurationSource.class);
                });
    }

    @Test
    @DisplayName("Should bind CORS properties correctly")
    void shouldBindCorsProperties() {
        this.contextRunner
                .withPropertyValues(
                        "khezy.api.cors.enabled=true",
                        "khezy.api.cors.allow-credentials=true",
                        "khezy.api.cors.allowed-origins=https://app.example.com",
                        "khezy.api.cors.allowed-methods=GET,POST",
                        "khezy.api.cors.max-age=3600",
                        "khezy.api.cors.path-pattern=/api/**"
                )
                .run(context -> {
                    final var props = context.getBean(KhezyCorsProperties.class);
                    assertThat(props.isAllowCredentials()).isTrue();
                    assertThat(props.getAllowedOrigins())
                            .containsExactly("https://app.example.com");
                    assertThat(props.getAllowedMethods())
                            .containsExactly("GET", "POST");
                    assertThat(props.getMaxAge()).isEqualTo(3600L);
                    assertThat(props.getPathPattern()).isEqualTo("/api/**");
                });
    }
}
