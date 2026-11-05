package io.github.khezyapp.crypto.exception;

/**
 * Key rotation could not be completed.
 */
public class KeyRotationException extends CryptoException {

    public KeyRotationException(final String message) {
        super(message);
    }

    public KeyRotationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
