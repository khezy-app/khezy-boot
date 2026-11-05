package io.github.khezyapp.crypto.rotation;

/**
 * Rotates keys and provides a re-encryptor for existing values.
 */
public interface KeyRotationService {

    RotationResult rotate(RotationRequest request);

    ReEncryptor reEncryptor(String oldKeyId);
}
