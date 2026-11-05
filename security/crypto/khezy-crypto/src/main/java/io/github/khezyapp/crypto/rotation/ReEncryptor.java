package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherContext;

/**
 * Re-encrypts stored values with the active key.
 */
@FunctionalInterface
public interface ReEncryptor {

    /**
     * Returns the new stored value, or the same value when it already uses the active key.
     */
    String reEncrypt(String storedValue, CipherContext context);
}
