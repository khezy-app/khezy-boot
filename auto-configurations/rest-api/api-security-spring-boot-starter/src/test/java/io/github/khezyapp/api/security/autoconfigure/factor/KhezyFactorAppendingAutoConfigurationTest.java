package io.github.khezyapp.api.security.autoconfigure.factor;

import io.github.khezyapp.api.security.authn.FactorAppendingAuthenticationManager;
import io.github.khezyapp.api.security.autoconfigure.KhezyFactorAppendingAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KhezyFactorAppendingAutoConfigurationTest {

    @Test
    void shouldBeAutoConfiguration() {
        assertThat(KhezyFactorAppendingAutoConfiguration.class
                .getAnnotation(org.springframework.boot.autoconfigure.AutoConfiguration.class))
                .isNotNull();
    }

    @Test
    void shouldHaveFactorAppendingManagerBeanMethod() {
        final var methods = Arrays.stream(KhezyFactorAppendingAutoConfiguration.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("factorAppendingAuthenticationManager"))
                .filter(m -> m.getReturnType().equals(FactorAppendingAuthenticationManager.class))
                .toList();
        assertThat(methods).hasSize(1);
    }

    @Test
    void shouldConditionalOnMissingBean() {
        final var method = Arrays.stream(KhezyFactorAppendingAutoConfiguration.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("factorAppendingAuthenticationManager"))
                .findFirst().orElseThrow();
        final var annotation = method.getAnnotation(ConditionalOnMissingBean.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains(FactorAppendingAuthenticationManager.class);
    }
}
