package io.github.khezyapp.a2a.core.client.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SendOptionsTest {

    @Test
    void defaultsShouldUseBlockingAndSixtySeconds() {
        final var options = SendOptions.defaults();

        assertEquals(SendOptions.DEFAULT_TIMEOUT, options.timeout());
        assertEquals(Duration.ofSeconds(60), options.timeout());
        assertEquals(SendOptions.Strategy.BLOCKING, options.strategy());
        assertTrue(options.stopOnInterrupt());
        assertTrue(options.headers().isEmpty());
        assertTrue(options.state().isEmpty());
    }

    @Test
    void constructorShouldCopyHeadersAndState() {
        final var headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer token");
        final var state = new LinkedHashMap<String, Object>();
        state.put("key", "value");

        final var options = new SendOptions(Duration.ofSeconds(5), SendOptions.Strategy.POLL, false, headers, state);

        headers.put("Authorization", "changed");
        state.put("key", "changed");

        assertEquals("Bearer token", options.headers().get("Authorization"));
        assertEquals("value", options.state().get("key"));
        assertFalse(options.stopOnInterrupt());
    }

    @Test
    void collectionsShouldBeImmutable() {
        final var options = new SendOptions(Duration.ofSeconds(5), SendOptions.Strategy.BLOCKING, true,
                Map.of("a", "b"), Map.of("c", "d"));

        assertThrows(UnsupportedOperationException.class, () -> options.headers().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> options.state().put("x", "y"));
    }

    @Test
    void nullCollectionsShouldBecomeEmpty() {
        final var options = new SendOptions(Duration.ofSeconds(5), SendOptions.Strategy.STREAM, true, null, null);

        assertTrue(options.headers().isEmpty());
        assertTrue(options.state().isEmpty());
    }

    @Test
    void toBuilderShouldCopyEveryComponent() {
        final var original = SendOptions.builder()
                .timeout(Duration.ofSeconds(7))
                .strategy(SendOptions.Strategy.POLL)
                .stopOnInterrupt(false)
                .headers(Map.of("X-Test", "1"))
                .state(Map.of("tenant", "t1"))
                .build();

        final var copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void shouldRejectNullAndNonPositiveTimeout() {
        assertThrows(NullPointerException.class, () -> SendOptions.builder().timeout(null).build());
        assertThrows(IllegalArgumentException.class,
                () -> SendOptions.builder().timeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                () -> SendOptions.builder().timeout(Duration.ofSeconds(-1)).build());
    }

    @Test
    void shouldRejectNullStrategy() {
        assertThrows(NullPointerException.class, () -> SendOptions.builder().strategy(null).build());
    }
}
