package io.github.khezyapp.crypto.exception;

/**
 * A requested key lifecycle transition is not allowed.
 */
public class KeyLifecycleException extends CryptoException {

    public KeyLifecycleException(final String message) {
        super(message);
    }
}
