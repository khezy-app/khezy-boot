package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.EncryptionCounter;
import io.github.khezyapp.crypto.contract.KeyManager;
import io.github.khezyapp.crypto.rotation.KeyRotationService;
import io.github.khezyapp.crypto.rotation.RotationContext;
import io.github.khezyapp.crypto.rotation.RotationLock;
import io.github.khezyapp.crypto.rotation.RotationPolicy;
import io.github.khezyapp.crypto.rotation.RotationReason;
import io.github.khezyapp.crypto.rotation.RotationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Periodically evaluates the rotation policy and rotates the active key when needed.
 *
 * <p>Only one instance should rotate. Use a shared {@link RotationLock} in multi-instance
 * deployments.</p>
 */
public class CryptoRotationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoRotationScheduler.class);
    private static final Duration LOCK_LEASE = Duration.ofMinutes(5);

    private final KeyManager keyManager;
    private final EncryptionCounter counter;
    private final RotationPolicy policy;
    private final KeyRotationService rotationService;
    private final RotationLock lock;
    private final CryptoProperties properties;
    private final Clock clock;

    public CryptoRotationScheduler(final KeyManager keyManager,
                                   final EncryptionCounter counter,
                                   final RotationPolicy policy,
                                   final KeyRotationService rotationService,
                                   final RotationLock lock,
                                   final CryptoProperties properties,
                                   final Clock clock) {
        this.keyManager = Objects.requireNonNull(keyManager, "keyManager");
        this.counter = Objects.requireNonNull(counter, "counter");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.rotationService = Objects.requireNonNull(rotationService, "rotationService");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${io.github.khezyapp.crypto.rotation.check-interval:PT1H}")
    public void checkRotation() {
        final var handle = lock.tryLock("khezy-crypto-rotation", LOCK_LEASE);
        if (handle.isEmpty()) {
            LOGGER.debug("Skipping rotation check; lock is held by another instance");
            return;
        }
        try (var ignored = handle.get()) {
            final var active = keyManager.activeKey();
            final var usage = counter.current(active.metadata().id());
            final var context = new RotationContext(active.metadata(), Instant.now(clock), usage);
            if (!policy.shouldRotate(context)) {
                return;
            }
            final var rotation = properties.getRotation();
            final var result = rotationService.rotate(RotationRequest.builder()
                    .mode(rotation.getMode())
                    .reason(RotationReason.SCHEDULED)
                    .actor("khezy-crypto-scheduler")
                    .retainOldFor(rotation.getRetainOldFor())
                    .build());
            LOGGER.info("Rotated crypto key '{}' -> '{}'", result.oldKeyId(), result.newKeyId());
        } catch (final RuntimeException e) {
            LOGGER.error("Crypto key rotation failed", e);
        }
    }
}
