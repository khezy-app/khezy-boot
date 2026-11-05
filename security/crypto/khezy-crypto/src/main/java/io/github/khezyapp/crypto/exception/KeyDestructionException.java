package io.github.khezyapp.crypto.exception;

/**
 * Key destruction was refused because a required confirmation was missing.
 */
public class KeyDestructionException extends CryptoException {

    public KeyDestructionException(final String message) {
        super(message);
    }
}
