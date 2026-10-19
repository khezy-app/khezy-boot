package io.github.khezyapp.crypto.contract;

import javax.crypto.SecretKey;
import java.util.Objects;

/**
 * A key's metadata plus its local key material.
 *
 * <p>{@link #key()} may be {@code null} for remote backends (KMS/HSM) where the material
 * never leaves the cryptographic module.</p>
 */
public final class KeyHandle {

    private final KeyMetadata metadata;
    private final SecretKey key;

    public KeyHandle(final KeyMetadata metadata, final SecretKey key) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.key = key;
    }

    public KeyMetadata metadata() {
        return metadata;
    }

    public SecretKey key() {
        return key;
    }

    @Override
    public String toString() {
        return "KeyHandle{metadata=" + metadata + '}';
    }
}
