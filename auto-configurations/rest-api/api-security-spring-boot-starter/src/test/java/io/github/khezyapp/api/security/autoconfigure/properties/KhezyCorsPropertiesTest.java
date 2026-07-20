package io.github.khezyapp.api.security.autoconfigure.properties;

import io.github.khezyapp.api.security.autoconfigure.KhezySecurityAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyCorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KhezySecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should bind default values correctly")
    void shouldBindDefaults() {
        this.contextRunner.run(context -> {
            final var props = context.getBean(KhezyCorsProperties.class);
            assertThat(props.isAllowCredentials()).isFalse();
            assertThat(props.getAllowedHeaders()).containsExactly("*");
            assertThat(props.getAllowedMethods())
                    .containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
            assertThat(props.getAllowPrivateNetwork()).isFalse();
        });
    }

    @Test
    @DisplayName("Should bind custom values from properties")
    void shouldBindCustomValues() {
        this.contextRunner
                .withPropertyValues(
                        "khezy.api.cors.allow-credentials=true",
                        "khezy.api.cors.allowed-origins=https://custom.com",
                        "khezy.api.cors.allowed-methods=GET,POST",
                        "khezy.api.cors.exposedheaders=X-Custom",
                        "khezy.api.cors.allow-private-network=true",
                        "khezy.api.cors.allowed-origin-patterns=https://*.example.com",
                        "khezy.api.cors.max-age=7200",
                        "khezy.api.cors.path-pattern=/api/**"
                )
                .run(context -> {
                    final var props = context.getBean(KhezyCorsProperties.class);
                    assertThat(props.isAllowCredentials()).isTrue();
                    assertThat(props.getAllowedOrigins())
                            .containsExactly("https://custom.com");
                    assertThat(props.getAllowedMethods())
                            .containsExactly("GET", "POST");
                    assertThat(props.getExposedHeaders())
                            .containsExactly("X-Custom");
                    assertThat(props.getAllowPrivateNetwork()).isTrue();
                    assertThat(props.getAllowedOriginPatterns())
                            .containsExactly("https://*.example.com");
                    assertThat(props.getMaxAge()).isEqualTo(7200L);
                    assertThat(props.getPathPattern()).isEqualTo("/api/**");
                });
    }
}
