package io.github.khezyapp.crypto.contract;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Logical context bound to a ciphertext.
 *
 * <p>{@code purpose} is a short label for policy and audit. {@code aad} is optional
 * associated data: it is authenticated but not stored, so the caller must supply the
 * exact same value when decrypting. Use it to bind a value to its row or tenant, for
 * example {@code a2a:task:<id>}.</p>
 *
 * @param purpose short purpose label, required
 * @param aad optional associated data, may be {@code null}
 */
public record CipherContext(String purpose, String aad) {

    private static final Pattern PURPOSE_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");

    public CipherContext {
        if (Objects.isNull(purpose) || !PURPOSE_PATTERN.matcher(purpose).matches()) {
            throw new IllegalArgumentException("purpose must match " + PURPOSE_PATTERN.pattern());
        }
    }

    public static CipherContext none() {
        return new CipherContext("none", null);
    }

    public static CipherContext of(final String purpose) {
        return new CipherContext(purpose, null);
    }

    public static CipherContext of(final String purpose, final String aad) {
        return new CipherContext(purpose, aad);
    }
}
