package io.github.khezyapp.crypto.exception;

/**
 * Authentication failed: the ciphertext, IV, tag, or associated data was modified.
 */
public class CiphertextIntegrityException extends CryptoException {

    public CiphertextIntegrityException(final String message) {
        super(message);
    }

    public CiphertextIntegrityException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
