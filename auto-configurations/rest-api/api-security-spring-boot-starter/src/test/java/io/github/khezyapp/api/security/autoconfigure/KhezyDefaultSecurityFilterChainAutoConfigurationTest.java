package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.autoconfigure.properties.KhezySecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyDefaultSecurityFilterChainAutoConfigurationTest {

    @Test
    void shouldBeAutoConfiguration() {
        assertThat(KhezyDefaultSecurityFilterChainAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class))
                .isNotNull();
    }

    @Test
    void shouldHaveConditionalOnMissingBeanSecurityFilterChain() {
        final var annotation = KhezyDefaultSecurityFilterChainAutoConfiguration.class
                .getAnnotation(ConditionalOnMissingBean.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(SecurityFilterChain.class);
    }

    @Test
    void shouldHaveConditionalOnPropertyEnabled() {
        final var annotation = KhezyDefaultSecurityFilterChainAutoConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("khezy.api.security");
        assertThat(annotation.name()).contains("enabled");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

    @Test
    void shouldEnableKhezySecurityProperties() {
        final var annotation = KhezyDefaultSecurityFilterChainAutoConfiguration.class
                .getAnnotation(EnableConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(KhezySecurityProperties.class);
    }

    @Test
    void shouldHaveDefaultSecurityFilterChainMethod() {
        final var methods = Arrays.stream(
                KhezyDefaultSecurityFilterChainAutoConfiguration.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("defaultSecurityFilterChain"))
                .filter(m -> m.getReturnType().equals(SecurityFilterChain.class))
                .toList();
        assertThat(methods).hasSize(1);
    }
}
