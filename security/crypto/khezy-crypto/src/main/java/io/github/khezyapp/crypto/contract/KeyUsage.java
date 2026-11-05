package io.github.khezyapp.crypto.contract;

/**
 * Intended purpose of a key. Keys must not be reused across purposes.
 */
public enum KeyUsage {

    /** Encrypts application data. */
    ENCRYPTION,

    /** Wraps data encryption keys. */
    KEY_WRAPPING
}
