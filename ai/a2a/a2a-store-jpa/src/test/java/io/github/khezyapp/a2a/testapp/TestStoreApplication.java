package io.github.khezyapp.a2a.testapp;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.StringEncryptor;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.DefaultStringEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test application that mimics a host with its own entity/repository alongside the A2A
 * JPA store auto-configuration.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestStoreApplication {

    /**
     * Supplies the crypto contract the store consumes. In a real application this bean
     * comes from the {@code khezy-crypto} starter.
     *
     * @return a working encryptor backed by an in-memory key provider
     */
    @Bean
    public StringEncryptor testStringEncryptor() {
        final var keyManager = new InMemoryKeyProvider();
        keyManager.create(KeyCreateRequest.builder().id("test-key").activateNow(true).build());
        return new DefaultStringEncryptor(
                new DefaultEncryptor(keyManager, new AesGcmBackend(),
                        new InMemoryEncryptionCounter()),
                new KhezyCipherTextCodec());
    }

    /**
     * Records finalized-task events for assertions.
     *
     * @return the recorder bean
     */
    @Bean
    public FinalizedEventRecorder finalizedEventRecorder() {
        return new FinalizedEventRecorder();
    }
}
