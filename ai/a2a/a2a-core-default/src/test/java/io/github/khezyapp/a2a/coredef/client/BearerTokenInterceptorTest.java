package io.github.khezyapp.a2a.coredef.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BearerTokenInterceptorTest {

    private final Object payload = new Object();

    @Test
    void shouldAddBearerTokenFromSupplier() {
        final var interceptor = new BearerTokenInterceptor(() -> "service-token");

        final var result = interceptor.intercept("message/send", payload, Map.of(), null, null);

        assertEquals("Bearer service-token", result.getHeaders().get("Authorization"));
        assertSame(payload, result.getPayload());
    }

    @Test
    void shouldResolveTokenFreshOnEveryCall() {
        final var counter = new AtomicInteger();
        final var interceptor = new BearerTokenInterceptor(() -> "token-" + counter.incrementAndGet());

        assertEquals("Bearer token-1", interceptor.intercept("m", payload, Map.of(), null, null).getHeaders()
                .get("Authorization"));
        assertEquals("Bearer token-2", interceptor.intercept("m", payload, Map.of(), null, null).getHeaders()
                .get("Authorization"));
    }

    @Test
    void shouldKeepPerCallAuthorizationOverSupplier() {
        final var interceptor = new BearerTokenInterceptor(() -> "service-token");
        final var headers = Map.of("Authorization", "Bearer caller-token");

        final var result = interceptor.intercept("message/send", payload, headers, null, null);

        assertEquals("Bearer caller-token", result.getHeaders().get("Authorization"));
    }

    @Test
    void shouldLeaveHeadersUntouchedWhenSupplierYieldsBlank() {
        final var interceptor = new BearerTokenInterceptor(() -> "   ");

        final var result = interceptor.intercept("message/send", payload, Map.of("X-Trace", "t1"), null, null);

        assertFalse(result.getHeaders().containsKey("Authorization"));
        assertEquals("t1", result.getHeaders().get("X-Trace"));
    }

    @Test
    void shouldLeaveHeadersUntouchedWhenSupplierYieldsNull() {
        final var interceptor = new BearerTokenInterceptor(() -> null);

        final var result = interceptor.intercept("message/send", payload, Map.of(), null, null);

        assertNull(result.getHeaders().get("Authorization"));
    }

    @Test
    void shouldTolerateNullIncomingHeaders() {
        final var interceptor = new BearerTokenInterceptor(() -> "service-token");

        final var result = interceptor.intercept("message/send", payload, null, null, null);

        assertEquals("Bearer service-token", result.getHeaders().get("Authorization"));
    }

    @Test
    void shouldPreserveExistingHeadersWhenAddingAuth() {
        final var incoming = new HashMap<String, String>();
        incoming.put("X-Trace", "trace-9");
        incoming.put("Accept", "application/json");
        final var interceptor = new BearerTokenInterceptor(() -> "tok");

        final var result = interceptor.intercept("message/send", payload, incoming, null, null);

        assertTrue(result.getHeaders().containsKey("Authorization"));
        assertEquals("trace-9", result.getHeaders().get("X-Trace"));
        assertEquals("application/json", result.getHeaders().get("Accept"));
    }

    @Test
    void shouldRejectNullSupplier() {
        assertThrows(NullPointerException.class, () -> new BearerTokenInterceptor(null));
    }
}
