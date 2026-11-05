package io.github.khezyapp.crypto.exception;

/**
 * Base class for all crypto failures.
 *
 * <p>Messages must never contain plaintext, key material, or raw ciphertext.</p>
 */
public class CryptoException extends RuntimeException {

    public CryptoException(final String message) {
        super(message);
    }

    public CryptoException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
