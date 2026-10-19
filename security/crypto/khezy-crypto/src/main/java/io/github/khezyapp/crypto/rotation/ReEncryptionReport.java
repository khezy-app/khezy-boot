package io.github.khezyapp.crypto.rotation;

/**
 * Counts from a re-encryption run.
 *
 * @param scanned records read
 * @param rewritten records rewritten with the active key
 * @param skipped records already using the active key
 * @param failed records that could not be migrated
 */
public record ReEncryptionReport(long scanned, long rewritten, long skipped, long failed) {
}
