package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.CipherContext;
import io.github.khezyapp.crypto.contract.Ciphertext;
import io.github.khezyapp.crypto.contract.KeyHandle;
import io.github.khezyapp.crypto.contract.KeyMetadata;
import io.github.khezyapp.crypto.contract.KeyStatus;
import io.github.khezyapp.crypto.contract.KeyUsage;
import io.github.khezyapp.crypto.exception.CiphertextIntegrityException;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmBackendTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] KEY = HEX.parseHex(
            "0000000000000000000000000000000000000000000000000000000000000000");
    private static final byte[] IV = HEX.parseHex("000000000000000000000000");
    private static final byte[] PLAINTEXT = HEX.parseHex("00000000000000000000000000000000");
    private static final byte[] EXPECTED_CIPHERTEXT = HEX.parseHex("cea7403d4d606b6e074ec5d3baf39d18");
    private static final byte[] EXPECTED_TAG = HEX.parseHex("d0d1c8a799996bf0265b98b5d48ab919");

    private final AesGcmBackend backend = new AesGcmBackend(null, new FixedIvGenerator(IV));
    private final KeyHandle key = key();

    @Test
    void shouldMatchNistKnownAnswer() {
        final var encrypted = backend.encrypt(PLAINTEXT, CipherContext.none(), key);
        assertArrayEquals(IV, encrypted.iv());
        assertArrayEquals(EXPECTED_CIPHERTEXT, encrypted.ciphertext());
        assertArrayEquals(EXPECTED_TAG, encrypted.tag());
        assertArrayEquals(PLAINTEXT, backend.decrypt(encrypted, CipherContext.none(), key));
    }

    @Test
    void shouldRoundTripWithAad() {
        final var context = CipherContext.of("users.ssn", "tenant=acme");
        final var encrypted = backend.encrypt(PLAINTEXT, context, key);
        assertArrayEquals(PLAINTEXT, backend.decrypt(encrypted, context, key));
        assertThrows(CiphertextIntegrityException.class,
                () -> backend.decrypt(encrypted, CipherContext.of("users.ssn", "tenant=beta"), key));
    }

    @Test
    void shouldDetectTampering() {
        final var encrypted = backend.encrypt(PLAINTEXT, CipherContext.none(), key);
        final var tamperedTag = encrypted.tag();
        tamperedTag[0] = (byte) (tamperedTag[0] ^ 1);
        final var tampered = new Ciphertext(encrypted.formatVersion(), encrypted.algorithmId(),
                encrypted.keyId(), encrypted.iv(), encrypted.ciphertext(), tamperedTag);
        assertThrows(CiphertextIntegrityException.class,
                () -> backend.decrypt(tampered, CipherContext.none(), key));
    }

    @Test
    void shouldRejectShortKey() {
        final var metadata = new KeyMetadata("k1", AesGcmBackend.ALGORITHM_ID,
                KeyUsage.ENCRYPTION, KeyStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH, null, null, 10);
        final var shortKey = new KeyHandle(metadata, new SecretKeySpec(new byte[16], "AES"));
        assertThrows(io.github.khezyapp.crypto.exception.CryptoConfigurationException.class,
                () -> backend.encrypt(PLAINTEXT, CipherContext.none(), shortKey));
    }

    private static KeyHandle key() {
        final var metadata = new KeyMetadata("k1", AesGcmBackend.ALGORITHM_ID,
                KeyUsage.ENCRYPTION, KeyStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH, null, null, 10);
        return new KeyHandle(metadata, new SecretKeySpec(KEY, "AES"));
    }

    private static final class FixedIvGenerator implements IvGenerator {

        private final byte[] iv;

        private FixedIvGenerator(final byte[] iv) {
            this.iv = iv;
        }

        @Override
        public byte[] nextIv(final int length) {
            return Arrays.copyOf(iv, length);
        }
    }
}
