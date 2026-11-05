package io.github.khezyapp.crypto.spring;

import io.github.khezyapp.crypto.rotation.KeyEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Spring event published for key lifecycle audit events.
 */
public class CryptoKeyEvent extends ApplicationEvent {

    private final KeyEvent keyEvent;

    public CryptoKeyEvent(final KeyEvent keyEvent) {
        super(keyEvent);
        this.keyEvent = keyEvent;
    }

    public KeyEvent keyEvent() {
        return keyEvent;
    }
}
