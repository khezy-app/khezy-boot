package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyProvider;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyUsage;
import io.github.khezyapp.crypto.exception.CryptoConfigurationException;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Read-only key provider backed by configuration properties.
 *
 * <p><strong>Development and tests only.</strong> Production keys must come from a key
 * vault, KMS, or HSM. Rotating a key here requires a restart and a configuration change.</p>
 */
public final class PropertiesKeyProvider implements KeyProvider {

    private static final Logger LOGGER = Logger.getLogger(PropertiesKeyProvider.class.getName());
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final Map<String, KeyHandle> keys = new LinkedHashMap<>();
    private final String activeKeyId;

    public PropertiesKeyProvider(final String activeKeyId,
                                 final Map<String, String> encodedKeys) {
        if (Objects.isNull(activeKeyId) || activeKeyId.isBlank()) {
            throw new CryptoConfigurationException("activeKeyId must not be blank");
        }
        if (Objects.isNull(encodedKeys) || encodedKeys.isEmpty()) {
            throw new CryptoConfigurationException("At least one key is required");
        }
        this.activeKeyId = activeKeyId;
        encodedKeys.forEach((id, encoded) -> keys.put(id, toHandle(id, encoded,
                id.equals(activeKeyId))));
        if (!keys.containsKey(activeKeyId)) {
            throw new CryptoConfigurationException("Active key '" + activeKeyId + "' is not configured");
        }
        LOGGER.warning("Property-based crypto keys are for development/tests only; "
                + "use a KMS or key vault in production");
    }

    @Override
    public Optional<KeyHandle> find(final String keyId) {
        return Objects.isNull(keyId) ? Optional.empty() : Optional.ofNullable(keys.get(keyId));
    }

    @Override
    public KeyHandle activeKey() {
        return keys.get(activeKeyId);
    }

    @Override
    public List<KeyMetadata> list() {
        return keys.values().stream().map(KeyHandle::metadata).toList();
    }

    private static KeyHandle toHandle(final String id,
                                      final String encoded,
                                      final boolean active) {
        if (Objects.isNull(encoded) || encoded.isBlank()) {
            throw new CryptoConfigurationException("Key '" + id + "' is blank");
        }
        final byte[] material;
        try {
            material = DECODER.decode(encoded);
        } catch (final IllegalArgumentException e) {
            throw new CryptoConfigurationException("Key '" + id + "' is not valid Base64url", e);
        }
        if (material.length != AesKeyGenerator.AES_256_BYTES) {
            throw new CryptoConfigurationException("Key '" + id + "' must be 32 bytes");
        }
        final var metadata = new KeyMetadata(id, AesGcmBackend.ALGORITHM_ID, KeyUsage.ENCRYPTION,
                active ? KeyStatus.ACTIVE : KeyStatus.DECRYPT_ONLY,
                Instant.EPOCH, active ? Instant.EPOCH : null, active ? null : Instant.EPOCH,
                null, AesKeyGenerator.DEFAULT_USAGE_LIMIT);
        return new KeyHandle(metadata, new SecretKeySpec(material, "AES"));
    }
}
