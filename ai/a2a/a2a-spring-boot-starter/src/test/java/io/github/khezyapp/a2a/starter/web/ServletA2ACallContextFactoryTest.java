package io.github.khezyapp.a2a.starter.web;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.khezyapp.a2a.core.server.A2AContextKeys;
import io.github.khezyapp.a2a.starter.A2AServerProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ServletA2ACallContextFactoryTest {

    private A2AServerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new A2AServerProperties();
        properties.getCallContext().setTenantHeader("X-Tenant");
        properties.getCallContext().setScopeHeader("X-Agent-Scope");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Spring Security authentication becomes an SDK AuthenticatedUser")
    void shouldMapAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        final var request = new MockHttpServletRequest("POST", "/a2a");
        request.addHeader("X-Custom", "value");

        final var context = new ServletA2ACallContextFactory(properties, new SpringSecurityUserResolver())
                .build(request);

        assertThat(context.getUser()).isInstanceOf(AuthenticatedUser.class);
        assertThat(context.getUser().getUsername()).isEqualTo("alice");
        assertThat(context.getUser().getAttribute("authorities")).isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    @DisplayName("Anonymous authentication becomes UnauthenticatedUser")
    void shouldMapAnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        final var request = new MockHttpServletRequest("POST", "/a2a");

        final var context = new ServletA2ACallContextFactory(properties, new SpringSecurityUserResolver())
                .build(request);

        assertThat(context.getUser()).isInstanceOf(UnauthenticatedUser.class);
        assertThat(context.getUser().isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("Headers, tenant, scope, extensions and version are propagated")
    void shouldPopulateCallContextState() {
        final var request = new MockHttpServletRequest("POST", "/a2a");
        request.addHeader("X-Custom", "value");
        request.addHeader("X-Tenant", "acme");
        request.addHeader("X-Agent-Scope", "prod:agent");
        request.addHeader("A2A-Version", "1.0");
        request.addHeader("A2A-Extensions", "https://example.com/ext, https://example.com/other");

        final var context = new ServletA2ACallContextFactory(properties, new SpringSecurityUserResolver())
                .build(request);

        assertThat(context.getState()).containsEntry(JSONRPCContextKeys.TENANT_KEY, "acme");
        assertThat(context.getState()).containsEntry(A2AContextKeys.AGENT_SCOPE, "prod:agent");
        assertThat(context.getState().get(JSONRPCContextKeys.HEADERS_KEY))
                .isInstanceOfSatisfying(Map.class, headers -> assertThat(headers).containsEntry("X-Custom", "value"));
        assertThat(context.getRequestedExtensions())
                .containsExactlyInAnyOrder("https://example.com/ext", "https://example.com/other");
        assertThat(context.getRequestedProtocolVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("Blank tenant and scope headers are not stored")
    void shouldSkipBlankRoutingHeaders() {
        final var request = new MockHttpServletRequest("POST", "/a2a");
        request.addHeader("X-Tenant", " ");
        request.addHeader("X-Agent-Scope", "");

        final var context = new ServletA2ACallContextFactory(properties, new SpringSecurityUserResolver())
                .build(request);

        assertThat(context.getState()).doesNotContainKeys(JSONRPCContextKeys.TENANT_KEY, A2AContextKeys.AGENT_SCOPE);
    }

    @Test
    @DisplayName("Disabled headers keep tenant and scope unresolved")
    void shouldIgnoreHeadersWhenNotConfigured() {
        final var disabled = new A2AServerProperties();
        final var request = new MockHttpServletRequest("POST", "/a2a");
        request.addHeader("X-Tenant", "acme");
        request.addHeader("X-Agent-Scope", "prod:agent");

        final var context = new ServletA2ACallContextFactory(disabled, new SpringSecurityUserResolver())
                .build(request);

        assertThat(context.getState()).doesNotContainKeys(JSONRPCContextKeys.TENANT_KEY, A2AContextKeys.AGENT_SCOPE);
    }
}
