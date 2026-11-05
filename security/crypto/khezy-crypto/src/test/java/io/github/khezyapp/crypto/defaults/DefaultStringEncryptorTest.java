package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultStringEncryptorTest {

    @Test
    void shouldRoundTripUtf8() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        final var encryptor = new DefaultStringEncryptor(
                new DefaultEncryptor(provider, new AesGcmBackend(), new InMemoryEncryptionCounter()),
                new KhezyCipherTextCodec());

        final var context = CipherContext.of("greeting", "tenant=khmer");
        final var encrypted = encryptor.encrypt("សួស្តី KHEZY", context);
        assertEquals("សួស្តី KHEZY", encryptor.decrypt(encrypted, context));
    }
}
