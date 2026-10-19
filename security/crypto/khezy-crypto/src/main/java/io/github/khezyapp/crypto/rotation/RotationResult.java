package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.KeyStatus;

/**
 * Outcome of a rotation.
 *
 * @param oldKeyId key that was active before the rotation
 * @param newKeyId key that is active after the rotation
 * @param mode strategy used
 * @param oldKeyStatus lifecycle status assigned to the old key
 * @param reEncryptionRequired whether existing data should be migrated
 */
public record RotationResult(String oldKeyId,
                             String newKeyId,
                             RotationMode mode,
                             KeyStatus oldKeyStatus,
                             boolean reEncryptionRequired) {
}
