package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.Encryptor;
import io.github.khezyapp.crypto.contract.StringEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * UTF-8 string facade over an {@link Encryptor} and a {@link CipherTextCodec}.
 */
public final class DefaultStringEncryptor implements StringEncryptor {

    private final Encryptor encryptor;
    private final CipherTextCodec codec;

    public DefaultStringEncryptor(final Encryptor encryptor,
                                  final CipherTextCodec codec) {
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public String encrypt(final String plaintext,
                          final CipherContext context) {
        Objects.requireNonNull(plaintext, "plaintext");
        final var ciphertext = encryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), context);
        return codec.encode(ciphertext);
    }

    @Override
    public String decrypt(final String ciphertext,
                          final CipherContext context) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        final var value = codec.decode(ciphertext);
        return new String(encryptor.decrypt(value, context), StandardCharsets.UTF_8);
    }
}
