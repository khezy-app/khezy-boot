package io.github.khezyapp.crypto.rotation;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyDestroyRequest;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.DefaultEncryptor;
import io.github.khezyapp.crypto.defaults.InMemoryEncryptionCounter;
import io.github.khezyapp.crypto.defaults.InMemoryKeyProvider;
import io.github.khezyapp.crypto.defaults.KhezyCipherTextCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultKeyRotationServiceTest {

    private InMemoryKeyProvider provider;
    private DefaultEncryptor encryptor;
    private DefaultKeyRotationService service;
    private KhezyCipherTextCodec codec;
    private final CipherContext context = CipherContext.of("test", "tenant=acme");

    @BeforeEach
    void setUp() {
        provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        codec = new KhezyCipherTextCodec();
        encryptor = new DefaultEncryptor(provider, new AesGcmBackend(), new InMemoryEncryptionCounter());
        service = new DefaultKeyRotationService(provider, new InMemoryEncryptionCounter(), encryptor, codec);
    }

    @Test
    void shouldRotateGradually() {
        final var oldValue = encryptor.encrypt("secret".getBytes(StandardCharsets.UTF_8), context);
        final var oldStored = codec.encode(oldValue);

        final var result = service.rotate(RotationRequest.builder()
                .newKeyId("k2")
                .mode(RotationMode.GRADUAL)
                .reason(RotationReason.SCHEDULED)
                .actor("tester")
                .retainOldFor(Duration.ofDays(90))
                .build());

        assertEquals("k1", result.oldKeyId());
        assertEquals("k2", result.newKeyId());
        assertEquals(KeyStatus.DECRYPT_ONLY, result.oldKeyStatus());
        assertFalse(result.reEncryptionRequired());
        assertEquals("k2", provider.activeKey().metadata().id());

        assertEquals("secret", new String(encryptor.decrypt(codec.decode(oldStored), context),
                StandardCharsets.UTF_8));
        assertNotEquals("k1", encryptor.encrypt(new byte[]{1}, context).keyId());
    }

    @Test
    void shouldRotateOnCompromise() {
        final var result = service.rotate(RotationRequest.builder()
                .newKeyId("k2")
                .reason(RotationReason.COMPROMISE)
                .actor("incident")
                .build());

        assertEquals(KeyStatus.COMPROMISED, result.oldKeyStatus());
        assertEquals(KeyStatus.COMPROMISED, provider.find("k1").orElseThrow().metadata().status());
    }

    @Test
    void shouldBeIdempotentForSameKey() {
        final var result = service.rotate(RotationRequest.builder()
                .newKeyId("k1")
                .reason(RotationReason.MANUAL)
                .actor("tester")
                .build());
        assertEquals("k1", result.oldKeyId());
        assertEquals("k1", result.newKeyId());
        assertFalse(result.reEncryptionRequired());
    }

    @Test
    void shouldReEncryptOldValues() {
        final var stored = codec.encode(encryptor.encrypt("secret".getBytes(StandardCharsets.UTF_8), context));
        service.rotate(RotationRequest.builder()
                .newKeyId("k2")
                .reason(RotationReason.SCHEDULED)
                .actor("tester")
                .build());

        final var migrated = service.reEncryptor("k1").reEncrypt(stored, context);
        assertTrue(migrated.startsWith("kzy:v1:AES-256-GCM:k2:"));
        assertEquals("secret", new String(encryptor.decrypt(codec.decode(migrated), context),
                StandardCharsets.UTF_8));
        assertEquals(migrated, service.reEncryptor("k1").reEncrypt(migrated, context));
    }

    @Test
    void shouldEmitAuditEvent() {
        final var events = new ArrayList<KeyEvent>();
        final var audited = new DefaultKeyRotationService(provider, new InMemoryEncryptionCounter(),
                encryptor, codec, java.time.Clock.systemUTC(), List.of(events::add));

        audited.rotate(RotationRequest.builder()
                .newKeyId("k2")
                .reason(RotationReason.SCHEDULED)
                .actor("tester")
                .build());

        assertEquals(1, events.size());
        assertEquals("ROTATED", events.get(0).type());
        assertEquals("k2", events.get(0).keyId());
        assertEquals("k1", events.get(0).relatedKeyId());
    }

    @Test
    void shouldRetireAndDestroyOldKey() {
        final var stored = codec.encode(encryptor.encrypt("secret".getBytes(StandardCharsets.UTF_8), context));
        service.rotate(RotationRequest.builder()
                .newKeyId("k2")
                .reason(RotationReason.SCHEDULED)
                .actor("tester")
                .build());
        service.reEncryptor("k1").reEncrypt(stored, context);

        provider.destroy(new KeyDestroyRequest("k1", "tester", "retention elapsed", true, true));
        assertEquals(KeyStatus.DESTROYED, provider.find("k1").orElseThrow().metadata().status());
        assertThrows(RuntimeException.class,
                () -> encryptor.decrypt(codec.decode(stored), context));
    }
}
