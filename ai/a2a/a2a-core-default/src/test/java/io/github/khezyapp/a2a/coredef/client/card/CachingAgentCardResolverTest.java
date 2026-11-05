package io.github.khezyapp.a2a.coredef.client.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.coredef.client.TestFixtures;

class CachingAgentCardResolverTest {

    private static final String URL = "http://localhost:8080";
    private static final AgentCard CARD =
            TestFixtures.card("weather", URL, null, "weather", false);

    @Test
    void freshEntryShouldNotHitTheSource() {
        final var source = new FakeSource(CardFetch.ok(CARD, "\"v1\"", "yesterday", null));
        final var resolver = new CachingAgentCardResolver(source, Duration.ofMinutes(5),
                new TestFixtures.MutableClock(Instant.parse("2026-10-19T00:00:00Z")));

        assertSame(CARD, resolver.resolve(URL).orElseThrow());
        assertSame(CARD, resolver.resolve(URL).orElseThrow());

        assertEquals(1, source.calls());
    }

    @Test
    void staleEntryShouldRevalidateWithConditionalHeaders() {
        final var clock = new TestFixtures.MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        final var source = new FakeSource(
                CardFetch.ok(CARD, "\"v1\"", "yesterday", null),
                CardFetch.notModified("\"v1\"", "yesterday", null));
        final var resolver = new CachingAgentCardResolver(source, Duration.ofSeconds(1), clock);

        resolver.resolve(URL);
        clock.advance(Duration.ofSeconds(2));
        final var resolved = resolver.resolve(URL);

        assertSame(CARD, resolved.orElseThrow());
        assertEquals(2, source.calls());
        assertEquals("\"v1\"", source.headers().get(1).etag());
        assertEquals("yesterday", source.headers().get(1).lastModified());
    }

    @Test
    void responseMaxAgeShouldOverrideTheDefaultTtl() {
        final var clock = new TestFixtures.MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        final var source = new FakeSource(
                CardFetch.ok(CARD, "\"v1\"", null, Duration.ofSeconds(10)),
                CardFetch.notModified("\"v1\"", null, Duration.ofSeconds(10)));
        final var resolver = new CachingAgentCardResolver(source, Duration.ofSeconds(1), clock);

        resolver.resolve(URL);
        clock.advance(Duration.ofSeconds(6));
        resolver.resolve(URL);
        assertEquals(1, source.calls());

        clock.advance(Duration.ofSeconds(5));
        resolver.resolve(URL);
        assertEquals(2, source.calls());
    }

    @Test
    void failedRevalidationShouldServeTheStaleCard() {
        final var clock = new TestFixtures.MutableClock(Instant.parse("2026-10-19T00:00:00Z"));
        final var source = new FakeSource(
                CardFetch.ok(CARD, "\"v1\"", null, null),
                CardFetch.failed());
        final var resolver = new CachingAgentCardResolver(source, Duration.ofSeconds(1), clock);

        resolver.resolve(URL);
        clock.advance(Duration.ofSeconds(2));

        assertSame(CARD, resolver.resolve(URL).orElseThrow());
    }

    @Test
    void failedFetchWithoutCacheShouldReturnEmpty() {
        final var source = new FakeSource(CardFetch.failed());
        final var resolver = new CachingAgentCardResolver(source);

        assertFalse(resolver.resolve(URL).isPresent());
    }

    @Test
    void invalidateAndClearShouldDropEntries() {
        final var source = new FakeSource(CardFetch.ok(CARD, null, null, null));
        final var resolver = new CachingAgentCardResolver(source);

        resolver.resolve(URL);
        assertEquals(1, resolver.size());
        resolver.invalidate(URL);
        assertEquals(0, resolver.size());

        resolver.resolve(URL);
        assertEquals(2, source.calls());
        resolver.clear();
        assertEquals(0, resolver.size());
    }

    private static final class FakeSource implements AgentCardSource {

        private final List<CardFetch> results;
        private final List<CardCacheHeader> headers = new ArrayList<>();
        private int calls;

        private FakeSource(final CardFetch... results) {
            this.results = List.of(results);
        }

        @Override
        public CardFetch fetch(final String baseUrl, final CardCacheHeader cacheHeader) {
            headers.add(cacheHeader);
            final CardFetch result = results.isEmpty()
                    ? CardFetch.failed()
                    : results.get(Math.min(calls, results.size() - 1));
            calls++;
            return result;
        }

        private int calls() {
            return calls;
        }

        private List<CardCacheHeader> headers() {
            return headers;
        }
    }
}
