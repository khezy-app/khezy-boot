package io.github.khezyapp.crypto.rotation;

import java.time.Duration;
import java.util.Objects;

/**
 * Request to rotate the active key.
 *
 * @param newKeyId optional requested id; generated when {@code null}
 * @param mode rotation strategy
 * @param reason why rotation is happening
 * @param actor person or system requesting the rotation
 * @param retainOldFor how long the old key remains available for decryption
 */
public record RotationRequest(String newKeyId,
                              RotationMode mode,
                              RotationReason reason,
                              String actor,
                              Duration retainOldFor) {

    public static final Duration DEFAULT_RETENTION = Duration.ofDays(90);

    public RotationRequest {
        mode = Objects.isNull(mode) ? RotationMode.GRADUAL : mode;
        Objects.requireNonNull(reason, "reason");
        if (Objects.isNull(actor) || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        retainOldFor = Objects.isNull(retainOldFor) ? DEFAULT_RETENTION : retainOldFor;
        if (retainOldFor.isNegative() || retainOldFor.isZero()) {
            throw new IllegalArgumentException("retainOldFor must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String newKeyId;
        private RotationMode mode = RotationMode.GRADUAL;
        private RotationReason reason = RotationReason.MANUAL;
        private String actor = "system";
        private Duration retainOldFor = DEFAULT_RETENTION;

        public Builder newKeyId(final String newKeyId) {
            this.newKeyId = newKeyId;
            return this;
        }

        public Builder mode(final RotationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder reason(final RotationReason reason) {
            this.reason = reason;
            return this;
        }

        public Builder actor(final String actor) {
            this.actor = actor;
            return this;
        }

        public Builder retainOldFor(final Duration retainOldFor) {
            this.retainOldFor = retainOldFor;
            return this;
        }

        public RotationRequest build() {
            return new RotationRequest(newKeyId, mode, reason, actor, retainOldFor);
        }
    }
}
