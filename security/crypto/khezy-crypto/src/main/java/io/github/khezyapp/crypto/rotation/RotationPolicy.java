package io.github.khezyapp.crypto.rotation;

/**
 * Decides whether the active key should be rotated.
 */
@FunctionalInterface
public interface RotationPolicy {

    boolean shouldRotate(RotationContext context);
}
