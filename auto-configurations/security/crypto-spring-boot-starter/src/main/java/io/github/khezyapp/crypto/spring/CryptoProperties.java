package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.defaults.AesGcmBackend;
import io.github.khezyapp.crypto.defaults.AesKeyGenerator;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;
import io.github.khezyapp.crypto.rotation.RotationMode;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Khezy Crypto.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "io.github.khezyapp.crypto")
public class CryptoProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoProperties.class);
    private static final Duration MAX_RECOMMENDED_CRYPTOPERIOD = Duration.ofDays(730);

    private boolean enabled = true;
    private String algorithm = AesGcmBackend.ALGORITHM_ID;
    private String activeKeyId;
    private Map<String, String> keys = new LinkedHashMap<>();
    private KeyProviderType keyProvider = KeyProviderType.PROPERTIES;
    private boolean failOnInsecureKeySource;
    private boolean requireAad = true;
    private Rotation rotation = new Rotation();

    public void validate() {
        if (!AesGcmBackend.ALGORITHM_ID.equals(algorithm)) {
            throw new CryptoConfigurationException("Only " + AesGcmBackend.ALGORITHM_ID
                    + " is supported in this version");
        }
        if (rotation.getUsageThreshold() <= 0.0 || rotation.getUsageThreshold() > 1.0) {
            throw new CryptoConfigurationException("rotation.usage-threshold must be in (0, 1]");
        }
        if (rotation.getCryptoperiod().isNegative() || rotation.getCryptoperiod().isZero()) {
            throw new CryptoConfigurationException("rotation.cryptoperiod must be positive");
        }
        if (rotation.getRetainOldFor().isNegative() || rotation.getRetainOldFor().isZero()) {
            throw new CryptoConfigurationException("rotation.retain-old-for must be positive");
        }
        if (rotation.getCryptoperiod().compareTo(MAX_RECOMMENDED_CRYPTOPERIOD) > 0) {
            LOGGER.warn("rotation.cryptoperiod is longer than the recommended maximum of 2 years");
        }
        if (keyProvider == KeyProviderType.PROPERTIES) {
            if (Objects.isNull(activeKeyId) || activeKeyId.isBlank()) {
                throw new CryptoConfigurationException("active-key-id is required for the properties provider");
            }
            if (keys.isEmpty()) {
                throw new CryptoConfigurationException("At least one key is required for the properties provider");
            }
        }
    }

    public enum KeyProviderType {
        PROPERTIES,
        IN_MEMORY,
        CUSTOM
    }

    @Getter
    @Setter
    public static class Rotation {

        private RotationMode mode = RotationMode.GRADUAL;
        private Duration cryptoperiod = Duration.ofDays(365);
        private long usageLimit = AesKeyGenerator.DEFAULT_USAGE_LIMIT;
        private double usageThreshold = 0.8;
        private Duration retainOldFor = Duration.ofDays(90);
        private boolean auto;
        private Duration checkInterval = Duration.ofHours(1);
        private int batchSize = 500;
    }
}
