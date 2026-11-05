package io.github.khezyapp.crypto.rotation;

import java.util.List;
import java.util.Objects;

/**
 * Rotates when any delegate policy says to rotate.
 */
public final class CompositeRotationPolicy implements RotationPolicy {

    private final List<RotationPolicy> policies;

    public CompositeRotationPolicy(final List<RotationPolicy> policies) {
        Objects.requireNonNull(policies, "policies");
        if (policies.isEmpty()) {
            throw new IllegalArgumentException("policies must not be empty");
        }
        this.policies = List.copyOf(policies);
    }

    @Override
    public boolean shouldRotate(final RotationContext context) {
        return policies.stream().anyMatch(policy -> policy.shouldRotate(context));
    }
}
