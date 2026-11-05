package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.Ciphertext;
import io.github.khezyapp.crypto.contract.CryptoBackend;
import io.github.khezyapp.crypto.contract.EncryptionCounter;
import io.github.khezyapp.crypto.contract.Encryptor;
import io.github.khezyapp.crypto.contract.KeyProvider;
import io.github.khezyapp.crypto.exception.KeyNotUsableException;
import io.github.khezyapp.crypto.exception.KeyUsageExceededException;
import io.github.khezyapp.crypto.exception.UnknownKeyException;

import java.util.Objects;

/**
 * Default {@link Encryptor}: selects the active key, enforces the usage limit, and
 * delegates the algorithm to a {@link CryptoBackend}.
 */
public final class DefaultEncryptor implements Encryptor {

    private final KeyProvider keyProvider;
    private final CryptoBackend backend;
    private final EncryptionCounter counter;

    public DefaultEncryptor(final KeyProvider keyProvider,
                            final CryptoBackend backend,
                            final EncryptionCounter counter) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.counter = Objects.requireNonNull(counter, "counter");
    }

    @Override
    public Ciphertext encrypt(final byte[] plaintext,
                              final CipherContext context) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(context, "context");
        final var key = keyProvider.activeKey();
        final var metadata = key.metadata();
        if (!metadata.isUsableForEncryption()) {
            throw new KeyNotUsableException(metadata.id(), "encryption");
        }
        final var usage = counter.incrementAndGet(metadata.id());
        if (usage > metadata.usageLimit()) {
            throw new KeyUsageExceededException(metadata.id(), metadata.usageLimit());
        }
        return backend.encrypt(plaintext, context, key);
    }

    @Override
    public byte[] decrypt(final Ciphertext ciphertext,
                          final CipherContext context) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        Objects.requireNonNull(context, "context");
        final var key = keyProvider.find(ciphertext.keyId())
                .orElseThrow(() -> new UnknownKeyException(ciphertext.keyId()));
        if (!key.metadata().isUsableForDecryption()) {
            throw new KeyNotUsableException(key.metadata().id(), "decryption");
        }
        return backend.decrypt(ciphertext, context, key);
    }

    @Override
    public String algorithmId() {
        return backend.algorithmId();
    }

    @Override
    public String activeKeyId() {
        return keyProvider.activeKey().metadata().id();
    }
}
