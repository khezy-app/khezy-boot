package io.github.khezyapp.crypto.rotation;

/**
 * Receives key lifecycle audit events.
 */
@FunctionalInterface
public interface AuditListener {

    void onKeyEvent(KeyEvent event);
}
