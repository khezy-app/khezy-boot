package io.github.khezyapp.crypto.defaults;

/**
 * Supplies initialization vectors.
 *
 * <p>Production code must use a cryptographically secure random generator. A
 * deterministic implementation exists only for known-answer tests.</p>
 */
public interface IvGenerator {

    byte[] nextIv(int length);
}
