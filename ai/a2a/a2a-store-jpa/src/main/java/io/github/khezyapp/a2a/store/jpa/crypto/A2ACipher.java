package io.github.khezyapp.a2a.store.jpa.crypto;

/**
 * Thin AAD-oriented encryption adapter kept for source compatibility.
 *
 * <p>Encryption itself is owned by {@code khezy-crypto}. New code should depend on
 * {@link io.github.khezyapp.crypto.contract.StringEncryptor} directly and build the
 * context with {@link A2ACipherContexts}; this interface only exists so hosts that
 * used the earlier A2A cipher contract keep compiling.</p>
 */
public interface A2ACipher {

    /**
     * Encrypts a payload bound to the supplied AAD.
     *
     * @param plaintext the serialized payload
     * @param aad the associated data identifying the row
     * @return the ciphertext envelope
     */
    String encrypt(String plaintext, String aad);

    /**
     * Decrypts a payload bound to the supplied AAD.
     *
     * @param ciphertext the ciphertext envelope
     * @param aad the associated data identifying the row
     * @return the serialized plaintext
     */
    String decrypt(String ciphertext, String aad);
}
