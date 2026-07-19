package io.github.khezyapp.api.security.crypto;

import lombok.Builder;

/**
 * Configuration data required to initialise a {@code TextEncryptor} instance.
 * Each entry is uniquely identified by its {@code version} and carries the
 * {@code password}, {@code salt}, and the {@code formatterId} that maps to a
 * {@link CipherTextFormatter} for serialising the encrypted result.
 *
 * @param version Logical version label (e.g. {@code "v1"}, {@code "v2"}) used as a lookup key.
 * @param formatterId Identifier that selects the {@link CipherTextFormatter} to use for this version.
 * @param password Secret password passed to {@code Encryptors.delux}.
 * @param salt Salt value passed to {@code Encryptors.delux}.
 */
@Builder
public record EncryptorData(
        String version,
        String formatterId,
        String password,
        String salt
) {
}
