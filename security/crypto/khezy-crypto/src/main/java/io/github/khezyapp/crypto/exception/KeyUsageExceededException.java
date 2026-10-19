package io.github.khezyapp.crypto.exception;

/**
 * A key reached its encryption usage limit and must be rotated.
 */
public class KeyUsageExceededException extends CryptoException {

    public KeyUsageExceededException(final String keyId, final long limit) {
        super("Key '" + keyId + "' reached its usage limit of " + limit);
    }
}
