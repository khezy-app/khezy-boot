package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Generates AES data encryption keys with {@link SecureRandom}.
 */
public final class AesKeyGenerator {

    public static final long DEFAULT_USAGE_LIMIT = 1L << 32;
    public static final int AES_256_BYTES = 32;

    private final SecureRandom secureRandom;

    public AesKeyGenerator() {
        this(new SecureRandom());
    }

    public AesKeyGenerator(final SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public KeyHandle generate(final KeyCreateRequest request) {
        Objects.requireNonNull(request, "request");
        final var now = Instant.now();
        final var id = Objects.isNull(request.id()) || request.id().isBlank()
                ? generateId(now)
                : request.id();
        final var status = request.activateNow() ? KeyStatus.ACTIVE : KeyStatus.PENDING;
        final var metadata = new KeyMetadata(id, request.algorithmId(), request.usage(), status,
                now, request.activateNow() ? now : null, null, null, DEFAULT_USAGE_LIMIT);
        return new KeyHandle(metadata, generateKey());
    }

    private SecretKey generateKey() {
        try {
            final var generator = KeyGenerator.getInstance("AES");
            generator.init(AES_256_BYTES * 8, secureRandom);
            return generator.generateKey();
        } catch (final GeneralSecurityException e) {
            throw new CryptoConfigurationException("Could not generate an AES key", e);
        }
    }

    private String generateId(final Instant now) {
        final var suffix = new byte[4];
        secureRandom.nextBytes(suffix);
        return "k-" + now.toEpochMilli() + "-" + HexFormat.of().formatHex(suffix);
    }
}
