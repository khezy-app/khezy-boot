package io.github.khezyapp.crypto.defaults;

import io.github.khezyapp.crypto.contract.Ciphertext;
import io.github.khezyapp.crypto.exception.CiphertextFormatException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KhezyCipherTextCodecTest {

    private final KhezyCipherTextCodec codec = new KhezyCipherTextCodec();

    @Test
    void shouldRoundTrip() {
        final var value = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{1, 2, 3}, new byte[]{4, 5}, new byte[]{6, 7});
        final var encoded = codec.encode(value);
        assertTrue(encoded.startsWith("kzy:v1:AES-256-GCM:k1:"));
        assertEquals(value, codec.decode(encoded));
    }

    @Test
    void shouldUseUrlSafeEncoding() {
        final var value = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{-1, -2, -3}, new byte[]{-4}, new byte[]{-5});
        final var encoded = codec.encode(value);
        assertTrue(encoded.indexOf('-') > 0 || encoded.indexOf('_') > 0);
        assertEquals(value, codec.decode(encoded));
    }

    @Test
    void shouldRejectMalformedInput() {
        assertThrows(CiphertextFormatException.class, () -> codec.decode(null));
        assertThrows(CiphertextFormatException.class, () -> codec.decode(""));
        assertThrows(CiphertextFormatException.class, () -> codec.decode("kzy:v1:only"));
        assertThrows(CiphertextFormatException.class,
                () -> codec.decode("kzy:v2:AES-256-GCM:k1:AAAA:AAAA:AAAA"));
        assertThrows(CiphertextFormatException.class,
                () -> codec.decode("kzy:v1:AES-256-GCM:k1:!!!!:AAAA:AAAA"));
    }

    @Test
    void shouldProduceExpectedFieldCount() {
        final var value = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3});
        final var encoded = codec.encode(value);
        assertEquals(7, encoded.split(":", -1).length);
        final var iv = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{1});
        assertTrue(encoded.contains(":" + iv + ":"));
    }
}
