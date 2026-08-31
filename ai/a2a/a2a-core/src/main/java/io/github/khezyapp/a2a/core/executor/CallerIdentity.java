package io.github.khezyapp.a2a.core.executor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Identity of the caller that initiated an A2A execution. */
public record CallerIdentity(Optional<String> subject, Set<String> scopes, Map<String, Object> claims) {

    public CallerIdentity {
        subject = Objects.requireNonNull(subject, "subject");
        scopes = Objects.requireNonNull(scopes, "scopes");
        claims = Objects.requireNonNull(claims, "claims");
    }

    public static CallerIdentity anonymous() {
        return new CallerIdentity(Optional.empty(), Set.of(), Map.of());
    }
}
