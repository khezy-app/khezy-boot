package io.github.khezyapp.crypto.rotation;

import java.time.Duration;
import java.util.Objects;

/**
 * Rotates when the active key has been in use for at least the configured cryptoperiod.
 */
public final class TimeBasedRotationPolicy implements RotationPolicy {

    private final Duration cryptoperiod;

    public TimeBasedRotationPolicy(final Duration cryptoperiod) {
        Objects.requireNonNull(cryptoperiod, "cryptoperiod");
        if (cryptoperiod.isNegative() || cryptoperiod.isZero()) {
            throw new IllegalArgumentException("cryptoperiod must be positive");
        }
        this.cryptoperiod = cryptoperiod;
    }

    @Override
    public boolean shouldRotate(final RotationContext context) {
        final var activatedAt = context.activeKey().activatedAt();
        return Objects.nonNull(activatedAt)
                && !context.now().isBefore(activatedAt.plus(cryptoperiod));
    }
}
