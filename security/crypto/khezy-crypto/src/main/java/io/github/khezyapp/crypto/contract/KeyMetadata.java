package io.github.khezyapp.crypto.contract;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Non-secret metadata for one key.
 *
 * @param id stable key identifier
 * @param algorithmId algorithm this key is authorized for
 * @param usage intended key purpose
 * @param status lifecycle state
 * @param createdAt creation time
 * @param activatedAt activation time, {@code null} until activated
 * @param retiredAt retirement time, {@code null} while active
 * @param destroyAfter earliest destruction time after retirement, may be {@code null}
 * @param usageLimit maximum encryption operations for this key
 */
public record KeyMetadata(String id,
                          String algorithmId,
                          KeyUsage usage,
                          KeyStatus status,
                          Instant createdAt,
                          Instant activatedAt,
                          Instant retiredAt,
                          Instant destroyAfter,
                          long usageLimit) {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    public KeyMetadata {
        if (Objects.isNull(id) || !KEY_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match " + KEY_ID_PATTERN.pattern());
        }
        if (Objects.isNull(algorithmId) || algorithmId.isBlank()) {
            throw new IllegalArgumentException("algorithmId must not be blank");
        }
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (usageLimit <= 0) {
            throw new IllegalArgumentException("usageLimit must be positive");
        }
    }

    public boolean isUsableForEncryption() {
        return status == KeyStatus.ACTIVE;
    }

    public boolean isUsableForDecryption() {
        return status == KeyStatus.ACTIVE
                || status == KeyStatus.DECRYPT_ONLY
                || status == KeyStatus.COMPROMISED;
    }
}
