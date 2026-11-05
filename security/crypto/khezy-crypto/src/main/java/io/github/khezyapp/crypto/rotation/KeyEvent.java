package io.github.khezyapp.crypto.rotation;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit record for a key lifecycle event.
 *
 * @param type event type, for example {@code ROTATED}
 * @param keyId subject key
 * @param relatedKeyId related key, if any
 * @param actor person or system
 * @param reason reason
 * @param at event time
 */
public record KeyEvent(String type,
                       String keyId,
                       String relatedKeyId,
                       String actor,
                       String reason,
                       Instant at) {

    public KeyEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(at, "at");
    }
}
