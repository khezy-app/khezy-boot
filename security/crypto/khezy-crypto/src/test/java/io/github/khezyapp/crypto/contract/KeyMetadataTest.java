package io.github.khezyapp.crypto.contract;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyMetadataTest {

    @Test
    void shouldReportUsability() {
        assertTrue(metadata(KeyStatus.ACTIVE).isUsableForEncryption());
        assertFalse(metadata(KeyStatus.DECRYPT_ONLY).isUsableForEncryption());
        assertFalse(metadata(KeyStatus.COMPROMISED).isUsableForEncryption());

        assertTrue(metadata(KeyStatus.ACTIVE).isUsableForDecryption());
        assertTrue(metadata(KeyStatus.DECRYPT_ONLY).isUsableForDecryption());
        assertTrue(metadata(KeyStatus.COMPROMISED).isUsableForDecryption());
        assertFalse(metadata(KeyStatus.DESTROYED).isUsableForDecryption());
    }

    @Test
    void shouldRejectInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new KeyMetadata("bad id!", "AES-256-GCM",
                KeyUsage.ENCRYPTION, KeyStatus.ACTIVE, Instant.now(), null, null, null, 10));
        assertThrows(IllegalArgumentException.class, () -> new KeyMetadata("k1", "AES-256-GCM",
                KeyUsage.ENCRYPTION, KeyStatus.ACTIVE, Instant.now(), null, null, null, 0));
    }

    private static KeyMetadata metadata(final KeyStatus status) {
        return new KeyMetadata("k1", "AES-256-GCM", KeyUsage.ENCRYPTION, status,
                Instant.now(), null, null, null, 10);
    }
}
