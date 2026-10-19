package io.github.khezyapp.api.security.crypto;

import io.github.khezyapp.crypto.contract.StringEncryptor;

import java.util.List;

/**
 * Factory that wires together a {@link DefaultEncryptorRegistry} and a
 * {@link CipherTextFormatterManager} into a ready-to-use
 * {@link CustomEncryptor}. Subclass or instantiate to expose as a Spring bean.
 */
public abstract class CustomEncryptors {

    /**
     * Creates a fully configured {@link CustomEncryptor} from the provided
     * encryptor data and cipher-text formatters.
     *
     * @param encryptorData   the versioned password/salt entries
     * @param cipherFormatters the available formatters for serialising cipher text
     * @return a new encryptor instance
     */
    public CustomEncryptor createEncryptor(
            final List<EncryptorData> encryptorData,
            final List<CipherTextFormatter> cipherFormatters
    ) {
        final var registry = new DefaultEncryptorRegistry(encryptorData);
        final var formatterManager = new CipherTextFormatterManager(cipherFormatters);
        return new CustomEncryptor(registry, formatterManager);
    }

    /**
     * Creates a new-format adapter backed by Khezy Crypto.
     *
     * @param stringEncryptor the shared string encryptor
     * @return a new encryptor instance
     */
    public CustomEncryptor createEncryptor(final StringEncryptor stringEncryptor) {
        return new CustomEncryptor(stringEncryptor);
    }
}
