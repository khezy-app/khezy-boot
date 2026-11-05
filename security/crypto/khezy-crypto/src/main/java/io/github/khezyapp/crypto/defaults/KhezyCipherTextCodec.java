package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherTextCodec;
import io.github.khezyapp.crypto.contract.Ciphertext;
import io.github.khezyapp.crypto.exception.CiphertextFormatException;

import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Default envelope codec:
 * {@code kzy:v1:<algorithmId>:<keyId>:<base64url(iv)>:<base64url(ciphertext)>:<base64url(tag)>}.
 */
public final class KhezyCipherTextCodec implements CipherTextCodec {

    public static final String PREFIX = "kzy";
    public static final String FORMAT_VERSION = "v1";

    private static final String SEPARATOR = ":";
    private static final int FIELD_COUNT = 7;
    private static final Pattern ALGORITHM_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @Override
    public String encode(final Ciphertext ciphertext) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        return String.join(SEPARATOR,
                PREFIX,
                FORMAT_VERSION,
                ciphertext.algorithmId(),
                ciphertext.keyId(),
                ENCODER.encodeToString(ciphertext.iv()),
                ENCODER.encodeToString(ciphertext.ciphertext()),
                ENCODER.encodeToString(ciphertext.tag()));
    }

    @Override
    public Ciphertext decode(final String value) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new CiphertextFormatException("Ciphertext must not be blank");
        }
        final var fields = value.split(SEPARATOR, -1);
        if (fields.length != FIELD_COUNT) {
            throw new CiphertextFormatException("Invalid ciphertext envelope");
        }
        if (!PREFIX.equals(fields[0]) || !FORMAT_VERSION.equals(fields[1])) {
            throw new CiphertextFormatException("Unsupported ciphertext envelope version");
        }
        final var algorithmId = fields[2];
        if (!ALGORITHM_PATTERN.matcher(algorithmId).matches()) {
            throw new CiphertextFormatException("Invalid algorithm id");
        }
        try {
            return new Ciphertext(FORMAT_VERSION, algorithmId, fields[3],
                    DECODER.decode(fields[4]),
                    DECODER.decode(fields[5]),
                    DECODER.decode(fields[6]));
        } catch (final IllegalArgumentException e) {
            throw new CiphertextFormatException("Invalid ciphertext encoding", e);
        }
    }
}
