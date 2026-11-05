package io.github.khezyapp.crypto.defaults;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Generates IVs with {@link SecureRandom}.
 */
public final class SecureRandomIvGenerator implements IvGenerator {

    private final SecureRandom secureRandom;

    public SecureRandomIvGenerator() {
        this(new SecureRandom());
    }

    public SecureRandomIvGenerator(final SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public byte[] nextIv(final int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        final var iv = new byte[length];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
