package io.github.khezyapp.crypto.contract;

import java.util.Objects;

/**
 * Request to destroy key material. Both confirmations are required; the library cannot
 * know whether any stored ciphertext still references the key.
 *
 * @param keyId key to destroy
 * @param actor person or system performing the destruction
 * @param reason human-readable reason
 * @param confirmNoCiphertextReferences caller confirms no live ciphertext uses the key
 * @param confirmBackupRetentionElapsed caller confirms the backup retention window passed
 */
public record KeyDestroyRequest(String keyId,
                                String actor,
                                String reason,
                                boolean confirmNoCiphertextReferences,
                                boolean confirmBackupRetentionElapsed) {

    public KeyDestroyRequest {
        if (Objects.isNull(keyId) || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (Objects.isNull(actor) || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (Objects.isNull(reason) || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
