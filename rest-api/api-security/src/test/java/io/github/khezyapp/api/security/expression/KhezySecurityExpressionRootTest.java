package io.github.khezyapp.api.security.expression;

import io.github.khezyapp.api.security.registry.AuthorizationRuleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KhezySecurityExpressionRootTest {

    private KhezySecurityExpressionRoot root;
    private AuthorizationRuleRegistry registry;

    @BeforeEach
    void setUp() {
        final var auth = mock(Authentication.class);
        root = new KhezySecurityExpressionRoot(auth);
        registry = mock(AuthorizationRuleRegistry.class);
        root.setAuthorizationRuleRegistry(registry);

        final var context = SecurityAttributeContext.builder()
                .additionalAttributes(Collections.singletonMap("tenant", "acme"))
                .build();
        root.setSecurityAttributeContext(context);
    }

    @Test
    void shouldDelegateCheckToRegistry() {
        when(registry.check("my-rule", root, "param1")).thenReturn(true);

        final var result = root.check("my-rule", "param1");

        assertThat(result).isTrue();
        verify(registry).check("my-rule", root, "param1");
    }

    @Test
    void shouldReturnFalseWhenNoRequestContext() {
        root.setSecurityAttributeContext(SecurityAttributeContext.builder().build());

        assertThat(root.hasHeaderValue("X-Tenant", "acme")).isFalse();
        assertThat(root.hasAnyHeaderValue("X-Tenant", List.of("acme"))).isFalse();
    }

    @Test
    void shouldMatchHeaderValue() {
        final var request = mock(HttpServletRequest.class);
        final var headers = Collections.enumeration(Collections.singletonList("acme"));
        when(request.getHeaders("X-Tenant")).thenReturn(headers);

        root.setSecurityAttributeContext(
                SecurityAttributeContext.builder().request(request).build()
        );

        assertThat(root.hasHeaderValue("X-Tenant", "acme")).isTrue();
        assertThat(root.hasHeaderValue("X-Tenant", "other")).isFalse();
    }

    @Test
    void shouldMatchAnyHeaderValue() {
        final var request = mock(HttpServletRequest.class);
        final var headers = Collections.enumeration(List.of("value1", "value2"));
        when(request.getHeaders("X-Scope")).thenReturn(headers);

        root.setSecurityAttributeContext(
                SecurityAttributeContext.builder().request(request).build()
        );

        assertThat(root.hasAnyHeaderValue("X-Scope", List.of("value1", "value3"))).isTrue();
        assertThat(root.hasAnyHeaderValue("X-Scope", List.of("value3", "value4"))).isFalse();
    }

    @Test
    void shouldReturnFalseForNullRequest() {
        root.setSecurityAttributeContext(SecurityAttributeContext.builder().build());

        assertThat(root.hasHeaderValue("X-Tenant", "acme")).isFalse();
    }

    @Test
    void shouldSetAndGetFilterObject() {
        final var obj = new Object();
        root.setFilterObject(obj);
        assertThat(root.getFilterObject()).isSameAs(obj);
    }

    @Test
    void shouldSetAndGetReturnObject() {
        final var obj = new Object();
        root.setReturnObject(obj);
        assertThat(root.getReturnObject()).isSameAs(obj);
    }

    @Test
    void shouldSetAndGetThis() {
        final var obj = new Object();
        root.setThis(obj);
        assertThat(root.getThis()).isSameAs(obj);
    }
}
