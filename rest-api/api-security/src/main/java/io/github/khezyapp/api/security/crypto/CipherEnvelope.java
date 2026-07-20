package io.github.khezyapp.api.security.crypto;

import lombok.Builder;

/**
 * Container that pairs {@link EncryptorData} with its resulting cipher text.
 * Produced during encryption and consumed during decryption to carry both
 * the algorithm metadata and the encrypted payload together.
 *
 * @param encryptorData Metadata identifying the encryptor version, formatter, password, and salt used.
 * @param cipherText The encrypted token or text payload.
 */
@Builder
public record CipherEnvelope(
        EncryptorData encryptorData,
        String cipherText
) {
}
