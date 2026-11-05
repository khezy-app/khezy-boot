package io.github.khezyapp.crypto.contract;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to cryptographic keys.
 *
 * <p>Implementations must be thread-safe and must return the same active key to every
 * instance (use a shared provider for multi-instance deployments).</p>
 */
public interface KeyProvider {

    Optional<KeyHandle> find(String keyId);

    KeyHandle activeKey();

    List<KeyMetadata> list();
}
