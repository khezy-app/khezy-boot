package io.github.khezyapp.api.security.expression;

import io.github.khezyapp.api.security.api.SecurityContextEnricher;
import io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KhezyMethodSecurityExpressionHandlerTest {

    private MethodInvocation invocation;

    @BeforeEach
    void setUp() {
        invocation = mock(MethodInvocation.class);
        when(invocation.getThis()).thenReturn(null);
        when(invocation.getMethod()).thenReturn(null);
    }

    @Test
    void shouldCreateExpressionRootWithEnrichers() {
        final var enricher1 = mock(SecurityContextEnricher.class);
        when(enricher1.getAdditionalContext()).thenReturn(Map.of("tenant", "acme"));
        final var enricher2 = mock(SecurityContextEnricher.class);
        when(enricher2.getAdditionalContext()).thenReturn(Map.of("region", "us-east"));

        final var registry = mock(AuthorizationRuleRegistry.class);
        final var handler = new KhezyMethodSecurityExpressionHandler(
                List.of(enricher1, enricher2), registry
        );

        final var root = (KhezySecurityExpressionRoot) handler.createSecurityExpressionRoot(
                mock(Authentication.class), invocation
        );

        assertThat(root.getAuthorizationRuleRegistry()).isSameAs(registry);
        assertThat(root.getSecurityAttributeContext()).isNotNull();
        assertThat(root.getSecurityAttributeContext().getAdditionalAttributes())
                .containsEntry("tenant", "acme")
                .containsEntry("region", "us-east");
    }

    @Test
    void shouldHandleNullEnricherContext() {
        final var enricher = mock(SecurityContextEnricher.class);
        when(enricher.getAdditionalContext()).thenReturn(null);

        final var registry = mock(AuthorizationRuleRegistry.class);
        final var handler = new KhezyMethodSecurityExpressionHandler(
                List.of(enricher), registry
        );

        final var root = (KhezySecurityExpressionRoot) handler.createSecurityExpressionRoot(
                mock(Authentication.class), invocation
        );

        assertThat(root.getSecurityAttributeContext().getAdditionalAttributes()).isEmpty();
    }

    @Test
    void shouldHandleEmptyEnrichersList() {
        final var registry = mock(AuthorizationRuleRegistry.class);
        final var handler = new KhezyMethodSecurityExpressionHandler(
                Collections.emptyList(), registry
        );

        final var root = (KhezySecurityExpressionRoot) handler.createSecurityExpressionRoot(
                mock(Authentication.class), invocation
        );

        assertThat(root.getSecurityAttributeContext().getAdditionalAttributes()).isEmpty();
    }

    @Test
    void shouldHandleNullEnrichersList() {
        final var registry = mock(AuthorizationRuleRegistry.class);
        final var handler = new KhezyMethodSecurityExpressionHandler(
                null, registry
        );

        final var root = (KhezySecurityExpressionRoot) handler.createSecurityExpressionRoot(
                mock(Authentication.class), invocation
        );

        assertThat(root).isNotNull();
        assertThat(root.getSecurityAttributeContext().getAdditionalAttributes()).isEmpty();
    }
}
