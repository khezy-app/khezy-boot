package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.rotation.KeyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoAuditEventPublisherTest {

    @Test
    void shouldPublishSpringEvent() {
        final List<ApplicationEvent> events = new ArrayList<>();
        final ApplicationEventPublisher publisher = event -> {
            if (event instanceof ApplicationEvent applicationEvent) {
                events.add(applicationEvent);
            }
        };
        final var bridge = new CryptoAuditEventPublisher(publisher);
        final var keyEvent = new KeyEvent("ROTATED", "k2", "k1", "tester", "SCHEDULED", Instant.now());

        bridge.onKeyEvent(keyEvent);

        assertEquals(1, events.size());
        assertEquals("k2", ((CryptoKeyEvent) events.get(0)).keyEvent().keyId());
    }
}
