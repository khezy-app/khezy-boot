package io.github.khezyapp.crypto.exception;

/**
 * A ciphertext references a key id the provider cannot resolve.
 */
public class UnknownKeyException extends CryptoException {

    public UnknownKeyException(final String keyId) {
        super("Unknown key id: " + keyId);
    }
}
