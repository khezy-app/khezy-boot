package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyUsage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    @Test
    void shouldRotateAfterCryptoperiod() {
        final var policy = new TimeBasedRotationPolicy(Duration.ofDays(30));
        assertFalse(policy.shouldRotate(context(NOW.minus(Duration.ofDays(29)))));
        assertTrue(policy.shouldRotate(context(NOW.minus(Duration.ofDays(30)))));
    }

    @Test
    void shouldRotateAtUsageThreshold() {
        final var policy = new UsageBasedRotationPolicy(0.8);
        assertFalse(policy.shouldRotate(new RotationContext(metadata(), NOW, 79)));
        assertTrue(policy.shouldRotate(new RotationContext(metadata(), NOW, 80)));
    }

    @Test
    void shouldCombinePolicies() {
        final var policy = new CompositeRotationPolicy(List.of(
                new TimeBasedRotationPolicy(Duration.ofDays(365)),
                new UsageBasedRotationPolicy(0.8)));
        assertTrue(policy.shouldRotate(new RotationContext(metadata(), NOW, 80)));
    }

    private static RotationContext context(final Instant activatedAt) {
        return new RotationContext(new KeyMetadata("k1", "AES-256-GCM", KeyUsage.ENCRYPTION,
                KeyStatus.ACTIVE, activatedAt, activatedAt, null, null, 100), NOW, 0);
    }

    private static KeyMetadata metadata() {
        return new KeyMetadata("k1", "AES-256-GCM", KeyUsage.ENCRYPTION, KeyStatus.ACTIVE,
                NOW, NOW, null, null, 100);
    }
}
