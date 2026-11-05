package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoPropertiesTest {

    @Test
    void shouldExposeDefaults() {
        final var properties = new CryptoProperties();
        assertTrue(properties.isEnabled());
        assertEquals("AES-256-GCM", properties.getAlgorithm());
        assertEquals(CryptoProperties.KeyProviderType.PROPERTIES, properties.getKeyProvider());
        assertTrue(properties.isRequireAad());
        assertEquals(0.8, properties.getRotation().getUsageThreshold());
    }

    @Test
    void shouldValidatePropertiesProvider() {
        final var properties = new CryptoProperties();
        assertThrows(CryptoConfigurationException.class, properties::validate);

        properties.setActiveKeyId("k1");
        properties.setKeys(Map.of("k1", "AAAA"));
        properties.validate();
    }

    @Test
    void shouldRejectUnsupportedAlgorithm() {
        final var properties = new CryptoProperties();
        properties.setActiveKeyId("k1");
        properties.setKeys(Map.of("k1", "AAAA"));
        properties.setAlgorithm("AES-CBC");
        assertThrows(CryptoConfigurationException.class, properties::validate);
    }

    @Test
    void shouldRejectInvalidThreshold() {
        final var properties = new CryptoProperties();
        properties.setActiveKeyId("k1");
        properties.setKeys(Map.of("k1", "AAAA"));
        properties.getRotation().setUsageThreshold(1.5);
        assertThrows(CryptoConfigurationException.class, properties::validate);
    }
}
