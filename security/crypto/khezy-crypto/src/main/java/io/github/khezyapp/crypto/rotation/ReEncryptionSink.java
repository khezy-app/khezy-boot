package io.github.khezyapp.crypto.rotation;

/**
 * Persists a migrated record.
 *
 * @param <T> record type
 */
@FunctionalInterface
public interface ReEncryptionSink<T> {

    void write(T record);
}
