package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.EncryptionCounter;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.contract.KeyStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Reports key health without exposing key material.
 */
public class CryptoHealthIndicator implements HealthIndicator {

    private final KeyManager keyManager;
    private final EncryptionCounter counter;
    private final CryptoProperties properties;
    private final Clock clock;

    public CryptoHealthIndicator(final KeyManager keyManager,
                                 final EncryptionCounter counter,
                                 final CryptoProperties properties,
                                 final Clock clock) {
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager");
        this.counter = Objects.requireNonNull(counter, "counter");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Health health() {
        try {
            final var key = keyManager.activeKey();
            final var metadata = key.metadata();
            final var usage = counter.current(metadata.id());
            final var limit = metadata.usageLimit();
            final var threshold = (long) Math.floor(limit * properties.getRotation().getUsageThreshold());
            final var age = Objects.isNull(metadata.activatedAt())
                    ? Duration.ZERO
                    : Duration.between(metadata.activatedAt(), Instant.now(clock));

            final var builder = Health.up()
                    .withDetail("keyId", metadata.id())
                    .withDetail("algorithm", metadata.algorithmId())
                    .withDetail("status", metadata.status())
                    .withDetail("ageSeconds", age.getSeconds())
                    .withDetail("usage", usage)
                    .withDetail("usageLimit", limit);

            if (metadata.status() != KeyStatus.ACTIVE) {
                return builder.status(org.springframework.boot.health.contributor.Status.DOWN).build();
            }
            if (usage >= threshold) {
                return builder.status(org.springframework.boot.health.contributor.Status.DOWN)
                        .withDetail("reason", "usage threshold reached")
                        .build();
            }
            return builder.build();
        } catch (final RuntimeException e) {
            return Health.down(e).build();
        }
    }
}
