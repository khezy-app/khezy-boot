package io.github.khezyapp.api.security.crypto;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that resolves a {@link CipherTextFormatter} by its format ID and
 * delegates {@code format} / {@code parse} operations. Acts as the central
 * dispatcher so callers never interact with individual formatters directly.
 */
public class CipherTextFormatterManager {
    private final Map<String, CipherTextFormatter> formatters;

    /**
     * Creates a manager backed by the given list of formatters.
     * Each formatter's {@link CipherTextFormatter#getFormatId()} is used as
     * the lookup key; duplicate IDs overwrite earlier entries.
     *
     * @param formatters the available formatters, may be {@code null}
     */
    public CipherTextFormatterManager(final List<CipherTextFormatter> formatters) {
        this.formatters = Optional.ofNullable(formatters)
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(CipherTextFormatter::getFormatId, Function.identity()));
    }

    /**
     * Serialises the cipher text using the formatter identified by the
     * encryptor data's {@code formatterId}.
     *
     * @param encryptorData metadata carrying the formatter ID, version, etc.
     * @param cipherText    the raw encrypted text
     * @return the formatted string
     * @throws IllegalArgumentException if no formatter is registered for the given ID
     */
    public String format(final EncryptorData encryptorData,
                         final String cipherText) {
        final var formatter = formatters.get(encryptorData.formatterId());
        if (Objects.isNull(formatter)) {
            throw new IllegalArgumentException("formatterId " + encryptorData.formatterId() + " not found");
        }
        return formatter.format(new CipherEnvelope(encryptorData, cipherText));
    }

    /**
     * Attempts to parse the cipher text by trying every registered formatter.
     * The first formatter that succeeds determines the result.
     *
     * @param cipherText the formatted cipher-text string
     * @return the parsed envelope
     * @throws IllegalStateException if no formatter can parse the input
     */
    public CipherEnvelope parse(final String cipherText) {
        for (final var formatter : formatters.values()) {
            try {
                return formatter.parse(cipherText);
            }  catch (final Exception ignored) {
            }
        }
        throw new IllegalStateException("No formatter able to parse cipherText");
    }
}
