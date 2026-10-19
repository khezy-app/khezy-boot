package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.Encryptor;

import java.util.Objects;

/**
 * Decrypts with the key identified by the stored value and re-encrypts with the active key.
 * Idempotent: a value already on the active key is returned unchanged.
 */
public final class DefaultReEncryptor implements ReEncryptor {

    private final Encryptor encryptor;
    private final CipherTextCodec codec;

    public DefaultReEncryptor(final Encryptor encryptor, final CipherTextCodec codec) {
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public String reEncrypt(final String storedValue, final CipherContext context) {
        Objects.requireNonNull(storedValue, "storedValue");
        Objects.requireNonNull(context, "context");
        final var current = codec.decode(storedValue);
        if (current.keyId().equals(encryptor.activeKeyId())) {
            return storedValue;
        }
        final var plaintext = encryptor.decrypt(current, context);
        return codec.encode(encryptor.encrypt(plaintext, context));
    }
}
