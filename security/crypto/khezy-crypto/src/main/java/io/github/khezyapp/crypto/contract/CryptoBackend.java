package io.github.khezyapp.crypto.contract;

/**
 * One cryptographic algorithm implementation.
 *
 * <p>A local backend uses {@link KeyHandle#key()}; a remote backend (KMS/HSM) uses
 * {@link KeyHandle#metadata()} and performs the operation inside the external module.</p>
 */
public interface CryptoBackend {

    String algorithmId();

    Ciphertext encrypt(byte[] plaintext, CipherContext context, KeyHandle key);

    byte[] decrypt(Ciphertext ciphertext, CipherContext context, KeyHandle key);
}
