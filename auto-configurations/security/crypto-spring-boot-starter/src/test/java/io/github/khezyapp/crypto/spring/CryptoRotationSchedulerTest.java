package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import io.github.khezyapp.crypto.rotation.AlwaysRotatePolicy;
import io.github.khezyapp.crypto.rotation.DefaultKeyRotationService;
import io.github.khezyapp.crypto.rotation.NoopRotationLock;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CryptoRotationSchedulerTest {

    @Test
    void shouldRotateWhenPolicyMatches() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var codec = new KhezyCipherTextCodec();
        final var encryptor = new DefaultEncryptor(provider, new AesGcmBackend(),
                new InMemoryEncryptionCounter());
        final var service = new DefaultKeyRotationService(provider,
                new InMemoryEncryptionCounter(), encryptor, codec);
        final var properties = new CryptoProperties();
        final var scheduler = new CryptoRotationScheduler(provider,
                new InMemoryEncryptionCounter(), new AlwaysRotatePolicy(), service,
                new NoopRotationLock(), properties, Clock.systemUTC());

        scheduler.checkRotation();

        assertNotEquals("k1", provider.activeKey().metadata().id());
    }
}
