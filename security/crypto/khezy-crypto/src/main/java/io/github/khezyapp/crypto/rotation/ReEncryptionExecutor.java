package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherContext;

import java.util.List;
import java.util.Objects;

/**
 * Batch helper for re-encrypting stored values. The consumer owns transactions, cursors,
 * and the source/sink implementations.
 */
public final class ReEncryptionExecutor {

    public <T extends ReEncryptionRecord<T>> ReEncryptionReport run(
            final ReEncryptionSource<T> source,
            final ReEncryptionSink<T> sink,
            final ReEncryptor reEncryptor,
            final CipherContext context,
            final int batchSize) {
        return run(source, sink, reEncryptor, context, batchSize, false);
    }

    public <T extends ReEncryptionRecord<T>> ReEncryptionReport run(
            final ReEncryptionSource<T> source,
            final ReEncryptionSink<T> sink,
            final ReEncryptor reEncryptor,
            final CipherContext context,
            final int batchSize,
            final boolean continueOnError) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(reEncryptor, "reEncryptor");
        Objects.requireNonNull(context, "context");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        long scanned = 0;
        long rewritten = 0;
        long skipped = 0;
        long failed = 0;
        while (true) {
            final List<T> batch = source.nextBatch();
            if (Objects.isNull(batch) || batch.isEmpty()) {
                break;
            }
            for (final T record : batch) {
                scanned++;
                try {
                    final var value = reEncryptor.reEncrypt(record.ciphertext(), context);
                    if (value.equals(record.ciphertext())) {
                        skipped++;
                    } else {
                        sink.write(record.withCiphertext(value));
                        rewritten++;
                    }
                } catch (final RuntimeException e) {
                    failed++;
                    if (!continueOnError) {
                        throw e;
                    }
                }
            }
        }
        return new ReEncryptionReport(scanned, rewritten, skipped, failed);
    }
}
