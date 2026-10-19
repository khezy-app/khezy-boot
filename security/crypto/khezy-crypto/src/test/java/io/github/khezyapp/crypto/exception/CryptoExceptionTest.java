package io.github.khezyapp.crypto.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoExceptionTest {

    @Test
    void shouldKeepMessagesFreeOfPayload() {
        assertTrue(new UnknownKeyException("k1").getMessage().contains("k1"));
        assertTrue(new KeyNotUsableException("k1", "encryption").getMessage()
                .contains("k1"));
        assertTrue(new KeyUsageExceededException("k1", 10).getMessage()
                .contains("10"));
    }
}
