package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.rotation.AuditListener;
import io.github.khezyapp.crypto.rotation.KeyEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/**
 * Bridges core audit events to Spring's {@link ApplicationEventPublisher}.
 */
public class CryptoAuditEventPublisher implements AuditListener {

    private final ApplicationEventPublisher publisher;

    public CryptoAuditEventPublisher(final ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void onKeyEvent(final KeyEvent event) {
        publisher.publishEvent(new CryptoKeyEvent(event));
    }
}
