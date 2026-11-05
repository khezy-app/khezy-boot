package io.github.khezyapp.crypto.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiphertextTest {

    @Test
    void shouldCopyByteArrays() {
        final var iv = new byte[]{1, 2, 3};
        final var ciphertext = new byte[]{4, 5, 6};
        final var tag = new byte[]{7, 8, 9};
        final var value = new Ciphertext("v1", "AES-256-GCM", "k1", iv, ciphertext, tag);

        iv[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, value.iv());

        final var returned = value.ciphertext();
        returned[0] = 99;
        assertArrayEquals(new byte[]{4, 5, 6}, value.ciphertext());
    }

    @Test
    void shouldCompareByContent() {
        final var first = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3});
        final var second = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void shouldRejectBlankMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new Ciphertext("", "AES-256-GCM", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3}));
        assertThrows(IllegalArgumentException.class, () -> new Ciphertext("v1", "", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3}));
    }

    @Test
    void shouldRejectInvalidKeyId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ciphertext("v1", "AES-256-GCM", "bad id!",
                        new byte[]{1}, new byte[]{2}, new byte[]{3}));
    }

    @Test
    void shouldRejectNullByteArrays() {
        assertThrows(IllegalArgumentException.class,
                () -> new Ciphertext("v1", "AES-256-GCM", "k1",
                        null, new byte[]{2}, new byte[]{3}));
    }

    @Test
    void shouldNotExposePayloadInToString() {
        final var value = new Ciphertext("v1", "AES-256-GCM", "k1",
                new byte[]{1}, new byte[]{2}, new byte[]{3});
        assertFalse(value.toString().contains("[1, 2, 3]"));
        assertTrue(value.toString().contains("keyId='k1'"));
    }
}
