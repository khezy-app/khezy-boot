package io.github.khezyapp.crypto.exception;

/**
 * A key exists but is not permitted for the requested operation.
 */
public class KeyNotUsableException extends CryptoException {

    public KeyNotUsableException(final String keyId, final String operation) {
        super("Key '" + keyId + "' is not usable for " + operation);
    }
}
