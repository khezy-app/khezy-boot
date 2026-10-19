package io.github.khezyapp.crypto.contract;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Request to create a key.
 *
 * @param id optional requested id; generated when {@code null}
 * @param algorithmId algorithm to generate for
 * @param usage intended purpose
 * @param activateNow whether the key becomes active immediately
 */
public record KeyCreateRequest(String id,
                               String algorithmId,
                               KeyUsage usage,
                               boolean activateNow) {

    public static final String DEFAULT_ALGORITHM = "AES-256-GCM";

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    public KeyCreateRequest {
        if (Objects.nonNull(id) && !KEY_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match " + KEY_ID_PATTERN.pattern());
        }
        algorithmId = Objects.isNull(algorithmId) || algorithmId.isBlank()
                ? DEFAULT_ALGORITHM
                : algorithmId;
        usage = Objects.isNull(usage) ? KeyUsage.ENCRYPTION : usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String algorithmId = DEFAULT_ALGORITHM;
        private KeyUsage usage = KeyUsage.ENCRYPTION;
        private boolean activateNow;

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder algorithmId(final String algorithmId) {
            this.algorithmId = algorithmId;
            return this;
        }

        public Builder usage(final KeyUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder activateNow(final boolean activateNow) {
            this.activateNow = activateNow;
            return this;
        }

        public KeyCreateRequest build() {
            return new KeyCreateRequest(id, algorithmId, usage, activateNow);
        }
    }
}
