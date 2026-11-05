package io.github.khezyapp.crypto.rotation;

/**
 * Policy for manual or compromise rotations.
 */
public final class AlwaysRotatePolicy implements RotationPolicy {

    @Override
    public boolean shouldRotate(final RotationContext context) {
        return true;
    }
}
