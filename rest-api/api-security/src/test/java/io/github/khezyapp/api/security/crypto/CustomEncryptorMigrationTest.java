package io.github.khezyapp.api.security.crypto;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.DefaultStringEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class CustomEncryptorMigrationTest {

    @Test
    void shouldReadLegacyFormat() {
        final var data = EncryptorData.builder()
                .version("v1")
                .formatterId("default")
                .password("password")
                .salt("5c0744940b5c369b")
                .build();
        final var registry = new DefaultEncryptorRegistry(List.of(data));
        final var formatter = new CipherTextFormatterManager(List.of(new DefaultCipherTextFormatter()));
        final var encryptor = new CustomEncryptor(registry, formatter);

        final var encrypted = encryptor.encrypt("v1", "secret");
        assertTrue(encrypted.startsWith("default.v1."));
        assertEquals("secret", encryptor.decrypt(encrypted));
        assertSame(registry.getTextEncryptor("v1"), registry.getTextEncryptor("v1"));
    }

    @Test
    void shouldUseNewFormatWhenConfigured() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var codec = new KhezyCipherTextCodec();
        final var stringEncryptor = new DefaultStringEncryptor(
                new DefaultEncryptor(provider, new AesGcmBackend(), new InMemoryEncryptionCounter()),
                codec);
        final var encryptor = new CustomEncryptor(stringEncryptor);

        final var encrypted = encryptor.encrypt("v1", "secret");
        assertTrue(encrypted.startsWith("kzy:v1:AES-256-GCM:k1:"));
        assertEquals("secret", encryptor.decrypt(encrypted));
        assertNotNull(codec.decode(encrypted));
    }

    @Test
    void shouldKeepContextStable() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var codec = new KhezyCipherTextCodec();
        final var stringEncryptor = new DefaultStringEncryptor(
                new DefaultEncryptor(provider, new AesGcmBackend(), new InMemoryEncryptionCounter()),
                codec);
        final var encryptor = new CustomEncryptor(stringEncryptor);

        final var encrypted = encryptor.encrypt("v1", "secret");
        assertEquals("secret", stringEncryptor.decrypt(encrypted, CipherContext.of("api-security")));
    }
}
