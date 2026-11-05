package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.Encryptor;
import io.github.khezyapp.crypto.contract.EncryptionCounter;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.exception.KeyNotUsableException;
import io.github.khezyapp.crypto.exception.KeyRotationException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Default rotation coordinator: creates a new key, atomically swaps the active key, retires
 * the old key, and emits audit events.
 */
public final class DefaultKeyRotationService implements KeyRotationService {

    private final KeyManager keyManager;
    private final EncryptionCounter counter;
    private final Encryptor encryptor;
    private final CipherTextCodec codec;
    private final Clock clock;
    private final List<AuditListener> listeners;

    public DefaultKeyRotationService(final KeyManager keyManager,
                                     final EncryptionCounter counter,
                                     final Encryptor encryptor,
                                     final CipherTextCodec codec) {
        this(keyManager, counter, encryptor, codec, Clock.systemUTC(), List.of());
    }

    public DefaultKeyRotationService(final KeyManager keyManager,
                                     final EncryptionCounter counter,
                                     final Encryptor encryptor,
                                     final CipherTextCodec codec,
                                     final Clock clock,
                                     final List<AuditListener> listeners) {
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager");
        this.counter = Objects.requireNonNull(counter, "counter");
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.listeners = Objects.nonNull(listeners) ? List.copyOf(listeners) : List.of();
    }

    @Override
    public RotationResult rotate(final RotationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.mode() == RotationMode.ENVELOPE_REWRAP) {
            throw new KeyRotationException("ENVELOPE_REWRAP is reserved for a future release");
        }
        final var current = keyManager.activeKey();
        final var oldKeyId = current.metadata().id();
        if (Objects.nonNull(request.newKeyId()) && request.newKeyId().equals(oldKeyId)) {
            return new RotationResult(oldKeyId, oldKeyId, request.mode(),
                    current.metadata().status(), false);
        }
        try {
            final var created = keyManager.create(KeyCreateRequest.builder()
                    .id(request.newKeyId())
                    .build());
            final var newKeyId = created.metadata().id();
            final var now = Instant.now(clock);
            final var previousStatus = isCompromise(request)
                    ? KeyStatus.COMPROMISED
                    : KeyStatus.DECRYPT_ONLY;
            final var context = new KeyTransitionContext(request.actor(),
                    request.reason().name(), now, now.plus(request.retainOldFor()));
            keyManager.swapActive(newKeyId, previousStatus, context);
            publish(new KeyEvent("ROTATED", newKeyId, oldKeyId, request.actor(), request.reason().name(), now));
            return new RotationResult(oldKeyId, newKeyId, request.mode(), previousStatus,
                    request.mode() != RotationMode.GRADUAL);
        } catch (final RuntimeException e) {
            throw new KeyRotationException("Key rotation failed", e);
        }
    }

    @Override
    public ReEncryptor reEncryptor(final String oldKeyId) {
        final var oldKey = keyManager.find(oldKeyId)
                .orElseThrow(() -> new KeyNotUsableException(oldKeyId, "re-encryption"));
        if (!oldKey.metadata().isUsableForDecryption()) {
            throw new KeyNotUsableException(oldKeyId, "re-encryption");
        }
        return new DefaultReEncryptor(encryptor, codec);
    }

    private static boolean isCompromise(final RotationRequest request) {
        return request.reason() == RotationReason.COMPROMISE
                || request.mode() == RotationMode.COMPROMISE;
    }

    private void publish(final KeyEvent event) {
        listeners.forEach(listener -> listener.onKeyEvent(event));
    }
}
