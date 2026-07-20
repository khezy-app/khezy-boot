package io.github.khezyapp.api.security.crypto;

import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Lookup service for obtaining {@link TextEncryptor} instances and their
 * associated {@link EncryptorData} by version label. Implementations cache
 * encryptors and resolve versioned passwords and salts.
 */
public interface EncryptorRegistry {

    /**
     * Returns (or creates and caches) a {@link TextEncryptor} for the given version.
     *
     * @param version the version label (e.g. {@code "v1"})
     * @return a ready-to-use text encryptor
     * @throws IllegalArgumentException if no encryptor data exists for the version
     */
    TextEncryptor getTextEncryptor(String version);

    /**
     * Returns the configuration data for the given version without creating
     * an encryptor.
     *
     * @param version the version label (e.g. {@code "v1"})
     * @return the stored encryptor data
     * @throws IllegalArgumentException if no data exists for the version
     */
    EncryptorData getEncryptorData(String version);
}
