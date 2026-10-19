package io.github.khezyapp.crypto.contract;

/**
 * Byte-oriented authenticated encryption contract.
 *
 * <p>Implementations must use the active key for encryption, select the key identified by
 * {@link Ciphertext#keyId()} for decryption, and fail closed on unknown keys or
 * authentication failures. Implementations must be thread-safe.</p>
 */
public interface Encryptor {

    /**
     * Encrypts the plaintext with the active key.
     *
     * @param plaintext data to encrypt
     * @param context logical context; its AAD is authenticated
     * @return ciphertext with key and algorithm metadata
     */
    Ciphertext encrypt(byte[] plaintext, CipherContext context);

    /**
     * Decrypts a ciphertext with the key identified by its key id.
     *
     * @param ciphertext encrypted value
     * @param context logical context; must match the encryption context
     * @return plaintext
     */
    byte[] decrypt(Ciphertext ciphertext, CipherContext context);

    /**
     * Algorithm identifier written into new ciphertexts.
     *
     * @return algorithm id, for example {@code AES-256-GCM}
     */
    String algorithmId();

    /**
     * Identifier of the key currently used for encryption.
     *
     * @return active key id
     */
    String activeKeyId();
}
