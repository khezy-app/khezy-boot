package io.github.khezyapp.crypto.contract;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit context for a key lifecycle transition.
 *
 * @param actor person or system performing the transition
 * @param reason human-readable reason
 * @param at transition time
 * @param destroyAfter earliest destruction time, may be {@code null}
 */
public record KeyTransitionContext(String actor, String reason, Instant at, Instant destroyAfter) {

    public KeyTransitionContext {
        if (Objects.isNull(actor) || actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        if (Objects.isNull(reason) || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        at = Objects.isNull(at) ? Instant.now() : at;
    }

    public KeyTransitionContext(final String actor, final String reason, final Instant at) {
        this(actor, reason, at, null);
    }
}
