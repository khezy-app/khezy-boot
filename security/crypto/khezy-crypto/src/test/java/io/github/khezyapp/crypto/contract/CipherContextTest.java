package io.github.khezyapp.crypto.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CipherContextTest {

    @Test
    void shouldCreateContexts() {
        assertNull(CipherContext.of("users.ssn").aad());
        assertEquals("tenant=acme", CipherContext.of("users.ssn", "tenant=acme").aad());
        assertEquals("none", CipherContext.none().purpose());
    }

    @Test
    void shouldRejectInvalidPurpose() {
        assertThrows(IllegalArgumentException.class, () -> CipherContext.of("bad purpose!"));
        assertThrows(IllegalArgumentException.class, () -> CipherContext.of(""));
        assertThrows(IllegalArgumentException.class, () -> CipherContext.of(null));
    }
}
