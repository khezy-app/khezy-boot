package io.github.khezyapp.crypto.contract;

/**
 * Lifecycle state of a cryptographic key.
 */
public enum KeyStatus {

    /** Generated but not yet used for encryption. */
    PENDING,

    /** Used for new encryption and for decryption. */
    ACTIVE,

    /** No longer used for encryption; retained for decryption and migration. */
    DECRYPT_ONLY,

    /** Suspected or confirmed compromised; decrypt only for urgent migration. */
    COMPROMISED,

    /** Key material destroyed; metadata may remain for audit. */
    DESTROYED
}
