package io.github.khezyapp.crypto.rotation;

/**
 * Rotates when usage reaches a fraction of the key's operation limit.
 */
public final class UsageBasedRotationPolicy implements RotationPolicy {

    private final double threshold;

    public UsageBasedRotationPolicy(final double threshold) {
        if (threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in (0, 1]");
        }
        this.threshold = threshold;
    }

    @Override
    public boolean shouldRotate(final RotationContext context) {
        final var limit = context.activeKey().usageLimit();
        return context.usageCount() >= Math.floor(limit * threshold);
    }
}
