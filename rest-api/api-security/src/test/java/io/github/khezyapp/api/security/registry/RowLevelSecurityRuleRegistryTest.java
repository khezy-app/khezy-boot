package io.github.khezyapp.api.security.registry;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import io.github.khezyapp.api.security.api.RowLevelSecurityRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RowLevelSecurityRuleRegistryTest {

    @Test
    void shouldDelegateToRegisteredRule() {
        final var rule = mock(RowLevelSecurityRule.class);
        when(rule.getName()).thenReturn("tenant-filter");

        final var registry = new RowLevelSecurityRuleRegistry(List.of(rule));
        final var root = mock(KhezySecurityExpressionRoot.class);
        final var annotation = mock(RowLevelSecurity.class);

        registry.enableFilter("tenant-filter", root, annotation, "arg1");

        verify(rule).enableFilter(root, annotation, new Object[]{"arg1"});
    }

    @Test
    void shouldThrowWhenRuleNotFound() {
        final var registry = new RowLevelSecurityRuleRegistry(List.of());

        assertThatThrownBy(() -> registry.enableFilter("unknown", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void shouldLookupByUppercaseName() {
        final var rule = mock(RowLevelSecurityRule.class);
        when(rule.getName()).thenReturn("TenantFilter");

        final var registry = new RowLevelSecurityRuleRegistry(List.of(rule));
        final var root = mock(KhezySecurityExpressionRoot.class);
        final var annotation = mock(RowLevelSecurity.class);

        registry.enableFilter("tenantfilter", root, annotation);

        verify(rule).enableFilter(root, annotation, new Object[]{});
    }
}
