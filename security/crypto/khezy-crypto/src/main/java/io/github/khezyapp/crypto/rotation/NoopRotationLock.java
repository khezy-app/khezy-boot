package io.github.khezyapp.crypto.rotation;

import java.time.Duration;
import java.util.Optional;

/**
 * Single-instance lock: always succeeds. Replace with a shared lock in multi-instance
 * deployments.
 */
public final class NoopRotationLock implements RotationLock {

    @Override
    public Optional<LockHandle> tryLock(final String name, final Duration lease) {
        return Optional.of(() -> {
        });
    }
}
