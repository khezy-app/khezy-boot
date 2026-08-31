package io.github.khezyapp.a2a.core.identity;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;

/**
 * Resolves the caller identity for the current call. Framework-neutral: the starter's
 * implementation reads Spring Security's {@code SecurityContextHolder}; custom
 * implementations may use thread-locals or other contextual state.
 */
public interface CallerIdentityResolver {

    CallerIdentity resolve();
}
