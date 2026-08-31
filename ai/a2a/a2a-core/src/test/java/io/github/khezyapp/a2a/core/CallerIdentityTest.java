package io.github.khezyapp.a2a.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CallerIdentityTest {

    @Test
    void anonymousShouldHaveEmptySubjectScopesAndClaims() {
        final CallerIdentity identity = CallerIdentity.anonymous();

        assertEquals(Optional.empty(), identity.subject());
        assertTrue(identity.scopes().isEmpty());
        assertTrue(identity.claims().isEmpty());
    }

    @Test
    void anonymousShouldBeConstructible() {
        final CallerIdentity identity = assertDoesNotThrow(CallerIdentity::anonymous);

        assertEquals(Optional.empty(), identity.subject());
    }

    @Test
    void shouldRejectNullSubject() {
        assertThrows(NullPointerException.class, () -> new CallerIdentity(null, Set.of(), Map.of()));
    }

    @Test
    void shouldRejectNullScopes() {
        assertThrows(NullPointerException.class, () -> new CallerIdentity(Optional.empty(), null, Map.of()));
    }

    @Test
    void shouldRejectNullClaims() {
        assertThrows(NullPointerException.class, () -> new CallerIdentity(Optional.empty(), Set.of(), null));
    }
}
