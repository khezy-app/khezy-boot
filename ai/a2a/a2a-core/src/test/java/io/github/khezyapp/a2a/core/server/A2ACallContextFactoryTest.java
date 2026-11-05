package io.github.khezyapp.a2a.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;
import org.junit.jupiter.api.Test;

class A2ACallContextFactoryTest {

    @Test
    void shouldPopulateStateFromRequestHooks() {
        final var headers = Map.of(
                A2AHeaders.A2A_EXTENSIONS, "ext-1, ext-2",
                A2AHeaders.A2A_VERSION, "1.2");
        final var request = new TestRequest(Optional.empty(), "tenant-1", "prod:agent", headers);

        final var context = new TestCallContextFactory().build(request);

        assertEquals(headers, context.getState().get(JSONRPCContextKeys.HEADERS_KEY));
        assertEquals("tenant-1", context.getState().get(JSONRPCContextKeys.TENANT_KEY));
        assertEquals("prod:agent", context.getState().get(A2AContextKeys.AGENT_SCOPE));
        assertEquals(TransportProtocol.JSONRPC, context.getState().get(ServerCallContext.TRANSPORT_KEY));
        assertEquals(Set.of("ext-1", "ext-2"), context.getRequestedExtensions());
        assertEquals("1.2", context.getRequestedProtocolVersion());
        assertSame(UnauthenticatedUser.INSTANCE, context.getUser());
    }

    @Test
    void shouldResolveAuthenticatedUser() {
        final var user = new AuthenticatedUser("alice");
        final var request = new TestRequest(Optional.of(user), null, null, Map.of());

        final var context = new TestCallContextFactory().build(request);

        assertSame(user, context.getUser());
    }

    @Test
    void shouldOmitBlankTenantAndScope() {
        final var request = new TestRequest(Optional.empty(), "  ", "", Map.of());

        final var context = new TestCallContextFactory().build(request);

        assertFalse(context.getState().containsKey(JSONRPCContextKeys.TENANT_KEY));
        assertFalse(context.getState().containsKey(A2AContextKeys.AGENT_SCOPE));
    }

    @Test
    void shouldUseEmptyDefaults() {
        final var context = new MinimalCallContextFactory().build("request");

        assertEquals(Map.of(), context.getState().get(JSONRPCContextKeys.HEADERS_KEY));
        assertFalse(context.getState().containsKey(JSONRPCContextKeys.TENANT_KEY));
        assertFalse(context.getState().containsKey(A2AContextKeys.AGENT_SCOPE));
        assertEquals(Set.of(), context.getRequestedExtensions());
        assertSame(UnauthenticatedUser.INSTANCE, context.getUser());
        assertNull(context.getRequestedProtocolVersion());
    }

    @Test
    void shouldRejectNullRequest() {
        final var factory = new MinimalCallContextFactory();

        assertThrows(NullPointerException.class, () -> factory.build(null));
    }

    private record TestRequest(
            Optional<User> user,
            String tenant,
            String scope,
            Map<String, String> headers) {
    }

    private static final class TestCallContextFactory extends A2ACallContextFactory<TestRequest> {

        @Override
        protected Optional<User> resolveUser(final TestRequest request) {
            return request.user();
        }

        @Override
        protected String resolveTenant(final TestRequest request) {
            return request.tenant();
        }

        @Override
        protected String resolveScope(final TestRequest request) {
            return request.scope();
        }

        @Override
        protected Map<String, String> extractHeaders(final TestRequest request) {
            return request.headers();
        }

        @Override
        protected String extractHeader(final TestRequest request,
                                       final String name) {
            return request.headers().get(name);
        }

        @Override
        protected List<String> extractHeaderValues(final TestRequest request,
                                                   final String name) {
            return extractHeader(request, name) == null
                    ? List.of()
                    : List.of(extractHeader(request, name).split(",\\s*"));
        }
    }

    private static final class MinimalCallContextFactory extends A2ACallContextFactory<String> {

        @Override
        protected Optional<User> resolveUser(final String request) {
            return Optional.empty();
        }
    }
}
