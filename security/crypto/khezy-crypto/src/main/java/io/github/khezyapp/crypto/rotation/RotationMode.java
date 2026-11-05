package io.github.khezyapp.crypto.rotation;

/**
 * Strategy used when rotating a key.
 */
public enum RotationMode {

    /** New writes use the new key; old values remain readable with old keys. */
    GRADUAL,

    /** Rotate and re-encrypt all existing values, then retire the old key. */
    EAGER,

    /** Rotate the key-encryption key and re-wrap data keys. */
    ENVELOPE_REWRAP,

    /** Treat the old key as compromised and migrate urgently. */
    COMPROMISE
}
