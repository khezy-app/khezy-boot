package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyDestroyRequest;
import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import io.github.khezyapp.crypto.exception.KeyDestructionException;
import io.github.khezyapp.crypto.exception.KeyLifecycleException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory key manager for tests and single-node development.
 *
 * <p>Do not use this as the source of truth in a multi-instance deployment; use a shared
 * provider (JDBC/KMS/Vault) instead.</p>
 */
public final class InMemoryKeyProvider implements KeyManager {

    private final ConcurrentHashMap<String, KeyHandle> keys = new ConcurrentHashMap<>();
    private final AesKeyGenerator generator;
    private volatile String activeKeyId;

    public InMemoryKeyProvider() {
        this(new AesKeyGenerator());
    }

    public InMemoryKeyProvider(final AesKeyGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public InMemoryKeyProvider(final KeyHandle active, final List<KeyHandle> others) {
        this();
        final var activeMetadata = active.metadata();
        if (!activeMetadata.isUsableForEncryption()) {
            throw new IllegalArgumentException("active key must be ACTIVE");
        }
        keys.put(activeMetadata.id(), active);
        activeKeyId = activeMetadata.id();
        others.forEach(key -> keys.put(key.metadata().id(), key));
    }

    @Override
    public Optional<KeyHandle> find(final String keyId) {
        return Objects.isNull(keyId) ? Optional.empty() : Optional.ofNullable(keys.get(keyId));
    }

    @Override
    public KeyHandle activeKey() {
        final var current = activeKeyId;
        if (Objects.isNull(current)) {
            throw new CryptoConfigurationException("No active key is configured");
        }
        final var key = keys.get(current);
        if (Objects.isNull(key)) {
            throw new CryptoConfigurationException("Active key '" + current + "' is missing");
        }
        return key;
    }

    @Override
    public List<KeyMetadata> list() {
        return keys.values().stream()
                .map(KeyHandle::metadata)
                .toList();
    }

    @Override
    public synchronized KeyHandle create(final KeyCreateRequest request) {
        final var generated = generator.generate(request);
        final var id = generated.metadata().id();
        if (keys.putIfAbsent(id, generated) != null) {
            throw new KeyLifecycleException("Key id already exists: " + id);
        }
        if (request.activateNow()) {
            if (Objects.nonNull(activeKeyId)) {
                keys.remove(id);
                throw new KeyLifecycleException("An active key already exists");
            }
            activeKeyId = id;
        }
        return generated;
    }

    @Override
    public synchronized void transition(final String keyId,
                                        final KeyStatus target,
                                        final KeyTransitionContext context) {
        final var current = keys.get(keyId);
        if (Objects.isNull(current)) {
            throw new KeyLifecycleException("Unknown key id: " + keyId);
        }
        validateTransition(current.metadata().status(), target);
        if (target == KeyStatus.ACTIVE && Objects.nonNull(activeKeyId) && !activeKeyId.equals(keyId)) {
            throw new KeyLifecycleException("Another key is already active: " + activeKeyId);
        }
        final var now = context.at();
        final var old = current.metadata();
        final var updated = new KeyMetadata(old.id(), old.algorithmId(), old.usage(), target,
                old.createdAt(),
                target == KeyStatus.ACTIVE ? now : old.activatedAt(),
                isRetired(target) ? now : old.retiredAt(),
                isRetired(target) ? context.destroyAfter() : old.destroyAfter(),
                old.usageLimit());
        keys.put(keyId, new KeyHandle(updated, current.key()));
        if (target == KeyStatus.ACTIVE) {
            activeKeyId = keyId;
        } else if (Objects.nonNull(activeKeyId) && activeKeyId.equals(keyId) && isRetired(target)) {
            activeKeyId = null;
        }
    }

    @Override
    public synchronized void swapActive(final String newKeyId,
                                        final KeyStatus previousStatus,
                                        final KeyTransitionContext context) {
        if (previousStatus != KeyStatus.DECRYPT_ONLY && previousStatus != KeyStatus.COMPROMISED) {
            throw new KeyLifecycleException("Previous status must be DECRYPT_ONLY or COMPROMISED");
        }
        if (Objects.isNull(newKeyId)) {
            throw new KeyLifecycleException("newKeyId must not be null");
        }
        final var next = keys.get(newKeyId);
        if (Objects.isNull(next)) {
            throw new KeyLifecycleException("Unknown key id: " + newKeyId);
        }
        if (next.metadata().status() != KeyStatus.PENDING
                && next.metadata().status() != KeyStatus.ACTIVE) {
            throw new KeyLifecycleException("Key '" + newKeyId + "' is not PENDING");
        }
        final var currentId = activeKeyId;
        if (Objects.isNull(currentId)) {
            throw new CryptoConfigurationException("No active key is configured");
        }
        if (currentId.equals(newKeyId)) {
            return;
        }
        final var current = keys.get(currentId);
        if (Objects.isNull(current)) {
            throw new CryptoConfigurationException("Active key '" + currentId + "' is missing");
        }
        final var now = context.at();
        final var currentMetadata = current.metadata();
        final var retired = new KeyMetadata(currentMetadata.id(), currentMetadata.algorithmId(),
                currentMetadata.usage(), previousStatus, currentMetadata.createdAt(),
                currentMetadata.activatedAt(), now, context.destroyAfter(),
                currentMetadata.usageLimit());
        keys.put(currentId, new KeyHandle(retired, current.key()));

        final var nextMetadata = next.metadata();
        final var activated = new KeyMetadata(nextMetadata.id(), nextMetadata.algorithmId(),
                nextMetadata.usage(), KeyStatus.ACTIVE, nextMetadata.createdAt(), now,
                null, null, nextMetadata.usageLimit());
        keys.put(newKeyId, new KeyHandle(activated, next.key()));
        activeKeyId = newKeyId;
    }

    @Override
    public synchronized void destroy(final KeyDestroyRequest request) {
        if (!request.confirmNoCiphertextReferences() || !request.confirmBackupRetentionElapsed()) {
            throw new KeyDestructionException("Key destruction requires both confirmations");
        }
        final var current = keys.get(request.keyId());
        if (Objects.isNull(current)) {
            throw new KeyDestructionException("Unknown key id: " + request.keyId());
        }
        validateTransition(current.metadata().status(), KeyStatus.DESTROYED);
        final var old = current.metadata();
        final var destroyed = new KeyMetadata(old.id(), old.algorithmId(), old.usage(),
                KeyStatus.DESTROYED, old.createdAt(), old.activatedAt(), old.retiredAt(),
                old.destroyAfter(), old.usageLimit());
        keys.put(request.keyId(), new KeyHandle(destroyed, null));
        if (request.keyId().equals(activeKeyId)) {
            activeKeyId = null;
        }
    }

    private static boolean isRetired(final KeyStatus status) {
        return status == KeyStatus.DECRYPT_ONLY
                || status == KeyStatus.COMPROMISED
                || status == KeyStatus.DESTROYED;
    }

    private static void validateTransition(final KeyStatus current, final KeyStatus target) {
        final var allowed = switch (current) {
            case PENDING -> target == KeyStatus.ACTIVE || target == KeyStatus.DESTROYED;
            case ACTIVE -> target == KeyStatus.DECRYPT_ONLY || target == KeyStatus.COMPROMISED;
            case DECRYPT_ONLY -> target == KeyStatus.DESTROYED;
            case COMPROMISED -> target == KeyStatus.DECRYPT_ONLY || target == KeyStatus.DESTROYED;
            case DESTROYED -> false;
        };
        if (!allowed) {
            throw new KeyLifecycleException("Transition " + current + " -> " + target + " is not allowed");
        }
    }
}
