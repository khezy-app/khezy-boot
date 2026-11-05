package io.github.khezyapp.crypto.contract;

/**
 * Serializes a {@link Ciphertext} to a portable string and parses it back.
 *
 * <p>Implementations must reject malformed input instead of guessing.</p>
 */
public interface CipherTextCodec {

    String encode(Ciphertext ciphertext);

    Ciphertext decode(String value);
}
