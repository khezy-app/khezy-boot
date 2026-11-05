package io.github.khezyapp.crypto.rotation;

import java.util.List;

/**
 * Supplies batches of records to migrate. Returning an empty list ends the migration.
 *
 * @param <T> record type
 */
@FunctionalInterface
public interface ReEncryptionSource<T> {

    List<T> nextBatch();
}
