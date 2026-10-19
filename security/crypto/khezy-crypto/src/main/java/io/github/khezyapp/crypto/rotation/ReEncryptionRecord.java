package io.github.khezyapp.crypto.rotation;

/**
 * Adapter implemented by the consumer's record type.
 *
 * @param <T> concrete record type
 */
public interface ReEncryptionRecord<T extends ReEncryptionRecord<T>> {

    String ciphertext();

    T withCiphertext(String value);
}
