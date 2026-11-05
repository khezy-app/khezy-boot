package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropertiesKeyProviderTest {

    private static final String KEY_ONE = encode(new byte[32]);
    private static final String KEY_TWO = encode(fill((byte) 1));

    @Test
    void shouldLoadActiveAndRetiredKeys() {
        final var provider = new PropertiesKeyProvider("k1", Map.of("k1", KEY_ONE, "k2", KEY_TWO));

        assertEquals("k1", provider.activeKey().metadata().id());
        assertEquals(KeyStatus.ACTIVE, provider.find("k1").orElseThrow().metadata().status());
        assertEquals(KeyStatus.DECRYPT_ONLY, provider.find("k2").orElseThrow().metadata().status());
    }

    @Test
    void shouldRejectWrongKeyLength() {
        final var shortKey = encode(new byte[16]);
        assertThrows(CryptoConfigurationException.class,
                () -> new PropertiesKeyProvider("k1", Map.of("k1", shortKey)));
    }

    @Test
    void shouldRejectMissingActiveKey() {
        assertThrows(CryptoConfigurationException.class,
                () -> new PropertiesKeyProvider("k9", Map.of("k1", KEY_ONE)));
    }

    private static String encode(final byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] fill(final byte value) {
        final var result = new byte[32];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
