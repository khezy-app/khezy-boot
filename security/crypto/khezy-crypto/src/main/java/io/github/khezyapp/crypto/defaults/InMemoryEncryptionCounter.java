package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.EncryptionCounter;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single-node counter. Multi-instance deployments must provide a shared implementation.
 */
public final class InMemoryEncryptionCounter implements EncryptionCounter {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long incrementAndGet(final String keyId) {
        return counters.computeIfAbsent(keyId, ignored -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public long current(final String keyId) {
        final var counter = counters.get(keyId);
        return Objects.isNull(counter) ? 0L : counter.get();
    }
}
