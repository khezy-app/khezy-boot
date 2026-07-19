package io.github.khezyapp.api.security.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * High-level encrypt / decrypt facade that combines an
 * {@link EncryptorRegistry} with a {@link CipherTextFormatterManager}.
 * Callers pass a version label and plain text and receive a fully
 * formatted cipher string (or the reverse).
 */
@RequiredArgsConstructor
public class CustomEncryptor {
    private final EncryptorRegistry registry;
    private final CipherTextFormatterManager formatterManager;

    /**
     * Encrypts the plain text using the encryptor registered for the given
     * version and returns a formatted cipher string.
     *
     * @param version   the version label identifying the password and salt to use
     * @param plainText the text to encrypt
     * @return the formatted cipher string (includes formatter ID and version)
     */
    public String encrypt(final String version,
                          final String plainText) {
        final var textEncryptor = getTextEncryptor(version);
        final var encryptorData = registry.getEncryptorData(version);
        final var cipherText = textEncryptor.encrypt(plainText);
        return formatterManager.format(encryptorData, cipherText);
    }

    /**
     * Decrypts a previously formatted cipher string back to plain text.
     * The formatter and version are extracted from the cipher string itself.
     *
     * @param cipherText the formatted cipher string produced by {@link #encrypt}
     * @return the original plain text
     */
    public String decrypt(final String cipherText) {
        final var envelope = formatterManager.parse(cipherText);
        final var textEncryptor = getTextEncryptor(envelope.encryptorData().version());
        return textEncryptor.decrypt(envelope.cipherText());
    }

    private TextEncryptor getTextEncryptor(final String version) {
        return registry.getTextEncryptor(version);
    }
}
