package io.github.khezyapp.crypto.contract;

/**
 * Tracks encryption operations per key so AES-GCM usage limits can be enforced.
 *
 * <p>Multi-instance deployments must use a shared counter, or rely on the KMS/HSM to
 * enforce the limit.</p>
 */
public interface EncryptionCounter {

    long incrementAndGet(String keyId);

    long current(String keyId);
}
