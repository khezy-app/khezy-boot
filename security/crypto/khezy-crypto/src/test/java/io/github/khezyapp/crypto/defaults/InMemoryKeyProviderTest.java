package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyCreateRequest;
import io.github.khezyapp.crypto.contract.KeyDestroyRequest;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyTransitionContext;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import io.github.khezyapp.crypto.exception.KeyDestructionException;
import io.github.khezyapp.crypto.exception.KeyLifecycleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKeyProviderTest {

    @Test
    void shouldCreateActiveAndRetiredKeys() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        provider.create(KeyCreateRequest.builder().id("k2").build());

        assertEquals("k1", provider.activeKey().metadata().id());
        assertEquals(KeyStatus.PENDING, provider.find("k2").orElseThrow().metadata().status());
        assertThrows(KeyLifecycleException.class,
                () -> provider.create(KeyCreateRequest.builder().id("k1").build()));
    }

    @Test
    void shouldRotateAndDestroy() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());

        provider.transition("k1", KeyStatus.DECRYPT_ONLY,
                new KeyTransitionContext("tester", "scheduled", Instant.now(), Instant.now().plusSeconds(60)));
        assertThrows(CryptoConfigurationException.class, provider::activeKey);
        assertTrue(provider.find("k1").orElseThrow().metadata().isUsableForDecryption());
        assertFalse(provider.find("k1").orElseThrow().metadata().isUsableForEncryption());

        assertThrows(KeyDestructionException.class,
                () -> provider.destroy(new KeyDestroyRequest("k1", "tester", "cleanup",
                        false, true)));

        provider.destroy(new KeyDestroyRequest("k1", "tester", "cleanup", true, true));
        assertEquals(KeyStatus.DESTROYED, provider.find("k1").orElseThrow().metadata().status());
        assertFalse(provider.find("k1").orElseThrow().metadata().isUsableForDecryption());
    }

    @Test
    void shouldRejectInvalidTransition() {
        final var provider = new InMemoryKeyProvider();
        provider.create(KeyCreateRequest.builder().id("k1").activateNow(true).build());
        assertThrows(KeyLifecycleException.class,
                () -> provider.transition("k1", KeyStatus.ACTIVE,
                        new KeyTransitionContext("tester", "invalid", Instant.now())));
    }
}
