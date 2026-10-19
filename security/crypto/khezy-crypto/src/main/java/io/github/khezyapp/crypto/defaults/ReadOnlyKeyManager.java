package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyDestroyRequest;
import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyProvider;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.exception.KeyRotationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts a read-only {@link KeyProvider} to {@link KeyManager} for property-backed keys.
 */
public final class ReadOnlyKeyManager implements KeyManager {

    private final KeyProvider delegate;

    public ReadOnlyKeyManager(final KeyProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<KeyHandle> find(final String keyId) {
        return delegate.find(keyId);
    }

    @Override
    public KeyHandle activeKey() {
        return delegate.activeKey();
    }

    @Override
    public List<KeyMetadata> list() {
        return delegate.list();
    }

    @Override
    public KeyHandle create(final KeyCreateRequest request) {
        throw new KeyRotationException("The configured key provider is read-only");
    }

    @Override
    public void transition(final String keyId,
                           final KeyStatus target,
                           final KeyTransitionContext context) {
        throw new KeyRotationException("The configured key provider is read-only");
    }

    @Override
    public void swapActive(final String newKeyId,
                           final KeyStatus previousStatus,
                           final KeyTransitionContext context) {
        throw new KeyRotationException("The configured key provider is read-only");
    }

    @Override
    public void destroy(final KeyDestroyRequest request) {
        throw new KeyRotationException("The configured key provider is read-only");
    }
}
