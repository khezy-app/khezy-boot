package io.github.khezyapp.crypto.contract;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Encrypted payload plus the metadata required to decrypt it.
 *
 * <p>The class is immutable and defensively copies every byte array. The key id and
 * algorithm id identify the key and backend used; neither contains secret material.</p>
 */
public final class Ciphertext {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private final String formatVersion;
    private final String algorithmId;
    private final String keyId;
    private final byte[] iv;
    private final byte[] ciphertext;
    private final byte[] tag;

    public Ciphertext(final String formatVersion,
                      final String algorithmId,
                      final String keyId,
                      final byte[] iv,
                      final byte[] ciphertext,
                      final byte[] tag) {
        this.formatVersion = requireText(formatVersion, "formatVersion");
        this.algorithmId = requireText(algorithmId, "algorithmId");
        this.keyId = requireKeyId(keyId);
        this.iv = copyRequired(iv, "iv");
        this.ciphertext = copyRequired(ciphertext, "ciphertext");
        this.tag = copyRequired(tag, "tag");
    }

    public String formatVersion() {
        return formatVersion;
    }

    public String algorithmId() {
        return algorithmId;
    }

    public String keyId() {
        return keyId;
    }

    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }

    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    public byte[] tag() {
        return Arrays.copyOf(tag, tag.length);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Ciphertext that)) {
            return false;
        }
        return formatVersion.equals(that.formatVersion)
                && algorithmId.equals(that.algorithmId)
                && keyId.equals(that.keyId)
                && Arrays.equals(iv, that.iv)
                && Arrays.equals(ciphertext, that.ciphertext)
                && Arrays.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(formatVersion, algorithmId, keyId);
        result = 31 * result + Arrays.hashCode(iv);
        result = 31 * result + Arrays.hashCode(ciphertext);
        result = 31 * result + Arrays.hashCode(tag);
        return result;
    }

    @Override
    public String toString() {
        return "Ciphertext{formatVersion='" + formatVersion + '\''
                + ", algorithmId='" + algorithmId + '\''
                + ", keyId='" + keyId + '\''
                + ", ivLength=" + iv.length
                + ", ciphertextLength=" + ciphertext.length
                + ", tagLength=" + tag.length
                + '}';
    }

    private static String requireText(final String value, final String name) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String requireKeyId(final String value) {
        final var id = requireText(value, "keyId");
        if (!KEY_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("keyId must match " + KEY_ID_PATTERN.pattern());
        }
        return id;
    }

    private static byte[] copyRequired(final byte[] value, final String name) {
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return Arrays.copyOf(value, value.length);
    }
}
