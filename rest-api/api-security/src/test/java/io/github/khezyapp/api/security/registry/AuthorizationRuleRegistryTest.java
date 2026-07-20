package io.github.khezyapp.api.security.registry;

import io.github.khezyapp.api.security.api.AuthorizationRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthorizationRuleRegistryTest {

    @Test
    void shouldRegisterRulesAndDelegateCheck() {
        final var rule = mock(AuthorizationRule.class);
        when(rule.getName()).thenReturn("my-rule");

        final var registry = new AuthorizationRuleRegistry(List.of(rule));
        final var root = mock(KhezySecurityExpressionRoot.class);
        when(rule.evaluate(root, new Object[]{"arg1"})).thenReturn(true);

        final var result = registry.check("my-rule", root, "arg1");

        assertThat(result).isTrue();
        verify(rule).evaluate(root, new Object[]{"arg1"});
    }

    @Test
    void shouldThrowWhenRuleNotFound() {
        final var registry = new AuthorizationRuleRegistry(List.of());

        final var root = mock(KhezySecurityExpressionRoot.class);

        assertThatThrownBy(() -> registry.check("non-existent", root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-existent");
    }

    @Test
    void shouldLookupByUppercaseName() {
        final var rule = mock(AuthorizationRule.class);
        when(rule.getName()).thenReturn("MyRule");

        final var registry = new AuthorizationRuleRegistry(List.of(rule));
        final var root = mock(KhezySecurityExpressionRoot.class);
        when(rule.evaluate(root, new Object[]{})).thenReturn(true);

        assertThat(registry.check("myrule", root)).isTrue();
        assertThat(registry.check("MYRULE", root)).isTrue();
        assertThat(registry.check("MyRule", root)).isTrue();
    }
}
