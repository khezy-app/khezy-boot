package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.KeyMetadata;

import java.time.Instant;
import java.util.Objects;

/**
 * Input for a {@link RotationPolicy}.
 *
 * @param activeKey currently active key metadata
 * @param now evaluation time
 * @param usageCount encryption operations recorded for the active key
 */
public record RotationContext(KeyMetadata activeKey, Instant now, long usageCount) {

    public RotationContext {
        Objects.requireNonNull(activeKey, "activeKey");
        Objects.requireNonNull(now, "now");
        if (usageCount < 0) {
            throw new IllegalArgumentException("usageCount must not be negative");
        }
    }
}
