package io.github.khezyapp.crypto.exception;

/**
 * A stored ciphertext is not a valid envelope.
 */
public class CiphertextFormatException extends CryptoException {

    public CiphertextFormatException(final String message) {
        super(message);
    }

    public CiphertextFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
