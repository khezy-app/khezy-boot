package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.exception.KeyUsageExceededException;
import io.github.khezyapp.crypto.exception.UnknownKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultEncryptorTest {

    @Test
    void shouldEncryptAndDecrypt() {
        final var provider = provider();
        final var encryptor = new DefaultEncryptor(provider, new AesGcmBackend(),
                new InMemoryEncryptionCounter());
        final var plaintext = "hello".getBytes(StandardCharsets.UTF_8);

        final var ciphertext = encryptor.encrypt(plaintext, CipherContext.none());
        assertArrayEquals(plaintext, encryptor.decrypt(ciphertext, CipherContext.none()));
        assertEquals("k1", encryptor.activeKeyId());
        assertEquals(AesGcmBackend.ALGORITHM_ID, encryptor.algorithmId());
    }

    @Test
    void shouldRejectUnknownKey() {
        final var encryptor = new DefaultEncryptor(provider(), new AesGcmBackend(),
                new InMemoryEncryptionCounter());
        final var ciphertext = encryptor.encrypt(new byte[]{1}, CipherContext.none());
        final var unknown = new io.github.khezyapp.crypto.contract.Ciphertext("v1",
                ciphertext.algorithmId(), "k2", ciphertext.iv(), ciphertext.ciphertext(),
                ciphertext.tag());
        assertThrows(UnknownKeyException.class,
                () -> encryptor.decrypt(unknown, CipherContext.none()));
    }

    @Test
    void shouldRejectUsageLimit() {
        final var limitedProvider = new InMemoryKeyProvider();
        limitedProvider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var limitedEncryptor = new DefaultEncryptor(new LimitedKeyProvider(limitedProvider),
                new AesGcmBackend(), new InMemoryEncryptionCounter());

        limitedEncryptor.encrypt(new byte[]{1}, CipherContext.none());
        limitedEncryptor.encrypt(new byte[]{1}, CipherContext.none());
        assertThrows(KeyUsageExceededException.class,
                () -> limitedEncryptor.encrypt(new byte[]{1}, CipherContext.none()));
    }

    private static InMemoryKeyProvider provider() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        return provider;
    }

    private static final class LimitedKeyProvider implements io.github.khezyapp.crypto.contract.KeyProvider {

        private final InMemoryKeyProvider delegate;

        private LimitedKeyProvider(final InMemoryKeyProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<io.github.khezyapp.crypto.contract.KeyHandle> find(final String keyId) {
            return delegate.find(keyId);
        }

        @Override
        public io.github.khezyapp.crypto.contract.KeyHandle activeKey() {
            final var key = delegate.activeKey();
            final var metadata = key.metadata();
            return new io.github.khezyapp.crypto.contract.KeyHandle(
                    new io.github.khezyapp.crypto.contract.KeyMetadata(metadata.id(),
                            metadata.algorithmId(), metadata.usage(), KeyStatus.ACTIVE,
                            metadata.createdAt(), metadata.activatedAt(), null, null, 2),
                    key.key());
        }

        @Override
        public java.util.List<io.github.khezyapp.crypto.contract.KeyMetadata> list() {
            return delegate.list();
        }
    }
}
