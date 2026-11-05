package io.github.khezyapp.a2a.store.jpa.crypto;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.StringEncryptor;

import java.util.Objects;

/**
 * {@link A2ACipher} adapter that delegates to a {@link StringEncryptor}.
 *
 * <p>No cryptographic code lives here: the adapter only maps a raw AAD string to the
 * crypto library's {@link CipherContext}.</p>
 */
public final class StringEncryptorA2ACipher implements A2ACipher {

    /** Purpose used when the adapter is created without an explicit one. */
    public static final String DEFAULT_PURPOSE = "a2a-store";

    private final StringEncryptor encryptor;
    private final String purpose;

    /**
     * Creates an adapter with the default purpose.
     *
     * @param encryptor the crypto library encryptor
     */
    public StringEncryptorA2ACipher(final StringEncryptor encryptor) {
        this(encryptor, DEFAULT_PURPOSE);
    }

    /**
     * Creates an adapter with an explicit purpose.
     *
     * @param encryptor the crypto library encryptor
     * @param purpose the purpose recorded in the cipher context
     */
    public StringEncryptorA2ACipher(final StringEncryptor encryptor, final String purpose) {
        this.encryptor = Objects.requireNonNull(encryptor, "encryptor must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
    }

    /**
     * Creates an adapter for task payloads.
     *
     * @param encryptor the crypto library encryptor
     * @return the task adapter
     */
    public static StringEncryptorA2ACipher tasks(final StringEncryptor encryptor) {
        return new StringEncryptorA2ACipher(encryptor, A2ACipherContexts.TASK_PURPOSE);
    }

    /**
     * Creates an adapter for push-notification config payloads.
     *
     * @param encryptor the crypto library encryptor
     * @return the push-config adapter
     */
    public static StringEncryptorA2ACipher pushConfigs(final StringEncryptor encryptor) {
        return new StringEncryptorA2ACipher(encryptor, A2ACipherContexts.PUSH_CONFIG_PURPOSE);
    }

    @Override
    public String encrypt(final String plaintext, final String aad) {
        return encryptor.encrypt(plaintext, CipherContext.of(purpose, aad));
    }

    @Override
    public String decrypt(final String ciphertext, final String aad) {
        return encryptor.decrypt(ciphertext, CipherContext.of(purpose, aad));
    }
}
