package io.github.khezyapp.crypto.contract;

/**
 * String-oriented convenience contract over {@link Encryptor} and {@link CipherTextCodec}.
 *
 * <p>Strings are encoded as UTF-8. The returned value is the portable envelope produced by
 * the codec, suitable for a database text column.</p>
 */
public interface StringEncryptor {

    String encrypt(String plaintext, CipherContext context);

    String decrypt(String ciphertext, CipherContext context);
}
