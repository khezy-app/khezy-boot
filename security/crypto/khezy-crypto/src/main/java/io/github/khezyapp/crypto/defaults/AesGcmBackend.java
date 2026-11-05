package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.Ciphertext;
import io.github.khezyapp.crypto.contract.CryptoBackend;
import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.exception.CiphertextIntegrityException;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import io.github.khezyapp.crypto.exception.CryptoException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.util.Arrays;
import java.util.Objects;

/**
 * AES-256-GCM backend using the JDK JCE provider by default.
 *
 * <p>Uses a 96-bit IV, a 128-bit authentication tag, and optional associated data. A new
 * {@link Cipher} instance is created for every operation, so the backend is thread-safe.</p>
 */
public final class AesGcmBackend implements CryptoBackend {

    public static final String ALGORITHM_ID = "AES-256-GCM";
    public static final int GCM_IV_BYTES = 12;
    public static final int GCM_TAG_BYTES = 16;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_BITS = GCM_TAG_BYTES * 8;

    private final Provider provider;
    private final IvGenerator ivGenerator;

    public AesGcmBackend() {
        this(null, new SecureRandomIvGenerator());
    }

    public AesGcmBackend(final Provider provider,
                         final IvGenerator ivGenerator) {
        this.provider = provider;
        this.ivGenerator = Objects.requireNonNull(ivGenerator, "ivGenerator");
    }

    @Override
    public String algorithmId() {
        return ALGORITHM_ID;
    }

    @Override
    public Ciphertext encrypt(final byte[] plaintext,
                              final CipherContext context,
                              final KeyHandle key) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(context, "context");
        final var secretKey = requireKey(key);
        final var iv = ivGenerator.nextIv(GCM_IV_BYTES);
        try {
            final var cipher = newCipher();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            applyAad(cipher, context);
            final var combined = cipher.doFinal(plaintext);
            final var ciphertextLength = combined.length - GCM_TAG_BYTES;
            final var ciphertext = Arrays.copyOf(combined, ciphertextLength);
            final var tag = Arrays.copyOfRange(combined, ciphertextLength, combined.length);
            return new Ciphertext("v1", ALGORITHM_ID, key.metadata().id(), iv, ciphertext, tag);
        } catch (final GeneralSecurityException e) {
            throw new CryptoException("AES-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(final Ciphertext ciphertext,
                          final CipherContext context,
                          final KeyHandle key) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        Objects.requireNonNull(context, "context");
        if (!ALGORITHM_ID.equals(ciphertext.algorithmId())) {
            throw new CryptoConfigurationException("Unsupported algorithm: " + ciphertext.algorithmId());
        }
        final var secretKey = requireKey(key);
        final var iv = ciphertext.iv();
        if (iv.length != GCM_IV_BYTES) {
            throw new CiphertextIntegrityException("Invalid GCM IV length");
        }
        if (ciphertext.tag().length != GCM_TAG_BYTES) {
            throw new CiphertextIntegrityException("Invalid GCM tag length");
        }
        final var combined = concat(ciphertext.ciphertext(), ciphertext.tag());
        try {
            final var cipher = newCipher();
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            applyAad(cipher, context);
            return cipher.doFinal(combined);
        } catch (final AEADBadTagException e) {
            throw new CiphertextIntegrityException("Ciphertext authentication failed", e);
        } catch (final GeneralSecurityException e) {
            throw new CiphertextIntegrityException("AES-GCM decryption failed", e);
        }
    }

    private Cipher newCipher() throws GeneralSecurityException {
        return Objects.isNull(provider)
                ? Cipher.getInstance(TRANSFORMATION)
                : Cipher.getInstance(TRANSFORMATION, provider);
    }

    private static void applyAad(final Cipher cipher,
                                 final CipherContext context) {
        if (Objects.nonNull(context.aad())) {
            cipher.updateAAD(context.aad().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static SecretKeySpec requireKey(final KeyHandle key) {
        Objects.requireNonNull(key, "key");
        final var secretKey = key.key();
        if (Objects.isNull(secretKey)) {
            throw new CryptoConfigurationException("Key material is not available for key '"
                    + key.metadata().id() + "'");
        }
        final var encoded = secretKey.getEncoded();
        if (Objects.isNull(encoded) || encoded.length != 32) {
            throw new CryptoConfigurationException("AES-256 requires a 32-byte key");
        }
        return new SecretKeySpec(encoded, KEY_ALGORITHM);
    }

    private static byte[] concat(final byte[] left,
                                 final byte[] right) {
        final var combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }
}
