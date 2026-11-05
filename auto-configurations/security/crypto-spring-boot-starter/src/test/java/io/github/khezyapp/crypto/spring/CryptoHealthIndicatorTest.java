package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoHealthIndicatorTest {

    @Test
    void shouldReportUpForActiveKey() {
        final var provider = provider();
        final var health = new CryptoHealthIndicator(provider, new InMemoryEncryptionCounter(),
                new CryptoProperties(), Clock.systemUTC()).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("k1", health.getDetails().get("keyId"));
    }

    @Test
    void shouldReportDownForCompromisedKey() {
        final var provider = provider();
        provider.transition("k1", KeyStatus.COMPROMISED,
                new KeyTransitionContext("tester", "incident", Instant.now()));
        final var health = new CryptoHealthIndicator(provider, new InMemoryEncryptionCounter(),
                new CryptoProperties(), Clock.systemUTC()).health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void shouldReportDownWhenNoActiveKey() {
        final var health = new CryptoHealthIndicator(new InMemoryKeyProvider(),
                new InMemoryEncryptionCounter(), new CryptoProperties(), Clock.systemUTC()).health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    private static InMemoryKeyProvider provider() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        return provider;
    }
}
