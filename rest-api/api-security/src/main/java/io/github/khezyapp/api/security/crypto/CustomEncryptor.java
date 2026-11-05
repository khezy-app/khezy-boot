package io.github.khezyapp.api.security.crypto;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.Objects;

/**
 * High-level encrypt / decrypt facade.
 *
 * <p><strong>Deprecated:</strong> new code should use
 * {@link io.github.khezyapp.crypto.contract.StringEncryptor} directly. This class remains
 * as a migration adapter for existing OAuth2 token persistence: it reads both the new
 * {@code kzy:v1:...} envelope and the legacy
 * {@code formatterId.version.cipherText} format.</p>
 *
 * @deprecated use {@link io.github.khezyapp.crypto.contract.StringEncryptor}
 */
@Deprecated
public class CustomEncryptor {

    private static final String KHEZY_PREFIX = "kzy:";
    private static final CipherContext CONTEXT = CipherContext.of("api-security");

    private final EncryptorRegistry registry;
    private final CipherTextFormatterManager formatterManager;
    private final StringEncryptor stringEncryptor;

    public CustomEncryptor(final EncryptorRegistry registry,
                           final CipherTextFormatterManager formatterManager) {
        this(registry, formatterManager, null);
    }

    public CustomEncryptor(final EncryptorRegistry registry,
                           final CipherTextFormatterManager formatterManager,
                           final StringEncryptor stringEncryptor) {
        this.registry = registry;
        this.formatterManager = formatterManager;
        this.stringEncryptor = stringEncryptor;
    }

    public CustomEncryptor(final StringEncryptor stringEncryptor) {
        this(null, null, Objects.requireNonNull(stringEncryptor, "stringEncryptor"));
    }

    /**
     * Encrypts the plain text using the new envelope when a {@link StringEncryptor} is
     * configured, otherwise the legacy versioned Spring Security encryptor.
     *
     * @param version   legacy version label; ignored by the new encryptor
     * @param plainText the text to encrypt
     * @return the formatted cipher string
     */
    public String encrypt(final String version,
                          final String plainText) {
        if (Objects.nonNull(stringEncryptor)) {
            return stringEncryptor.encrypt(plainText, CONTEXT);
        }
        final var textEncryptor = getTextEncryptor(version);
        final var encryptorData = requireRegistry().getEncryptorData(version);
        final var cipherText = textEncryptor.encrypt(plainText);
        return requireFormatterManager().format(encryptorData, cipherText);
    }

    /**
     * Decrypts a new-format or legacy cipher string.
     *
     * @param cipherText the formatted cipher string
     * @return the original plain text
     */
    public String decrypt(final String cipherText) {
        if (Objects.nonNull(stringEncryptor) && cipherText.startsWith(KHEZY_PREFIX)) {
            return stringEncryptor.decrypt(cipherText, CONTEXT);
        }
        final var envelope = requireFormatterManager().parse(cipherText);
        final var textEncryptor = getTextEncryptor(envelope.encryptorData().version());
        return textEncryptor.decrypt(envelope.cipherText());
    }

    private TextEncryptor getTextEncryptor(final String version) {
        return requireRegistry().getTextEncryptor(version);
    }

    private EncryptorRegistry requireRegistry() {
        if (Objects.isNull(registry)) {
            throw new IllegalStateException("No legacy EncryptorRegistry is configured");
        }
        return registry;
    }

    private CipherTextFormatterManager requireFormatterManager() {
        if (Objects.isNull(formatterManager)) {
            throw new IllegalStateException("No legacy CipherTextFormatterManager is configured");
        }
        return formatterManager;
    }
}
