package io.github.khezyapp.crypto.contract;

/**
 * Lifecycle operations for keys.
 *
 * <p>Implementations must validate state transitions and make activation atomic across
 * instances sharing the same provider.</p>
 */
public interface KeyManager extends KeyProvider {

    KeyHandle create(KeyCreateRequest request);

    void transition(String keyId, KeyStatus target, KeyTransitionContext context);

    /**
     * Atomically activates {@code newKeyId} and retires the currently active key.
     *
     * @param newKeyId key to activate
     * @param previousStatus status for the previous active key
     *                       ({@link KeyStatus#DECRYPT_ONLY} or {@link KeyStatus#COMPROMISED})
     * @param context audit context, including the destroy-after deadline
     */
    void swapActive(String newKeyId, KeyStatus previousStatus, KeyTransitionContext context);

    void destroy(KeyDestroyRequest request);
}
