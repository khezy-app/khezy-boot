package io.github.khezyapp.crypto.rotation;

import java.time.Duration;
import java.util.Optional;

/**
 * Cross-instance lock used by automated rotation so only one node rotates at a time.
 */
public interface RotationLock {

    Optional<LockHandle> tryLock(String name, Duration lease);

    /**
     * Handle for an acquired lock.
     */
    interface LockHandle extends AutoCloseable {

        @Override
        void close();
    }
}
