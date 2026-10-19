package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReEncryptionExecutorTest {

    @Test
    void shouldRewriteInBatches() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var codec = new KhezyCipherTextCodec();
        final var encryptor = new DefaultEncryptor(provider, new AesGcmBackend(),
                new InMemoryEncryptionCounter());
        final var context = CipherContext.of("test");
        final var oldValues = List.of(
                codec.encode(encryptor.encrypt("a".getBytes(StandardCharsets.UTF_8), context)),
                codec.encode(encryptor.encrypt("b".getBytes(StandardCharsets.UTF_8), context)));

        final var rotation = new DefaultKeyRotationService(provider, new InMemoryEncryptionCounter(),
                encryptor, codec);
        rotation.rotate(RotationRequest.builder().newKeyId("k2").build());

        final var records = new ArrayList<>(oldValues.stream().map(Value::new).toList());
        final var written = new ArrayList<Value>();
        final var report = new ReEncryptionExecutor().run(
                new BatchSource(records),
                written::add,
                rotation.reEncryptor("k1"),
                context,
                2);

        assertEquals(2, report.scanned());
        assertEquals(2, report.rewritten());
        assertEquals(0, report.failed());
        assertEquals(2, written.size());
        written.forEach(value -> assertEquals("k2", codec.decode(value.value()).keyId()));
    }

    private record Value(String value) implements ReEncryptionRecord<Value> {

        @Override
        public String ciphertext() {
            return value;
        }

        @Override
        public Value withCiphertext(final String ciphertext) {
            return new Value(ciphertext);
        }
    }

    private static final class BatchSource implements ReEncryptionSource<Value> {

        private final List<Value> values;
        private int index;

        private BatchSource(final List<Value> values) {
            this.values = values;
        }

        @Override
        public List<Value> nextBatch() {
            if (index >= values.size()) {
                return List.of();
            }
            final var batch = values.subList(index, Math.min(index + 2, values.size()));
            index += batch.size();
            return batch;
        }
    }
}
