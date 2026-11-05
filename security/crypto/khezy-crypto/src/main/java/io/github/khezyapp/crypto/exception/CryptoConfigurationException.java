package io.github.khezyapp.crypto.exception;

/**
 * Invalid or incomplete crypto configuration, such as a missing active key.
 */
public class CryptoConfigurationException extends CryptoException {

    public CryptoConfigurationException(final String message) {
        super(message);
    }

    public CryptoConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
