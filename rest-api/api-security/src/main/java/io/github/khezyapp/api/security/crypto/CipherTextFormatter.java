package io.github.khezyapp.api.security.crypto;

/**
 * Strategy for serialising a {@link CipherEnvelope} to a portable string and
 * parsing it back. Each implementation is identified by a unique format ID
 * so that encrypted values can carry their own formatting rules.
 */
public interface CipherTextFormatter {

    /** Unique identifier for this formatter (e.g. {@code "default"}). */
    String getFormatId();

    /**
     * Serialises the given envelope into a portable string representation.
     *
     * @param envelope the cipher envelope containing metadata and encrypted text
     * @return the formatted string, ready for storage or transmission
     */
    String format(CipherEnvelope envelope);

    /**
     * Reconstructs a {@link CipherEnvelope} from its serialised form.
     *
     * @param cipherText the formatted string produced by {@link #format(CipherEnvelope)}
     * @return the parsed envelope with metadata and encrypted text
     * @throws IllegalStateException if the string cannot be parsed by this formatter
     */
    CipherEnvelope parse(String cipherText);
}
