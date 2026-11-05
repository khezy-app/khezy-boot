package io.github.khezyapp.a2a.core.server;

/**
 * Application-owned keys stored in the SDK {@code ServerCallContext} state.
 *
 * <p>The A2A SDK validates tenant values against {@code [a-zA-Z0-9_.-]}. Composite
 * application scopes (for example {@code prod:agent}) therefore cannot travel as the
 * SDK tenant and use {@link #AGENT_SCOPE} instead. Tenant is routing; agent scope is
 * persistence isolation.</p>
 */
public final class A2AContextKeys {

    /**
     * Call-context state key holding the application-owned agent scope.
     */
    public static final String AGENT_SCOPE = "agent_scope";

    private A2AContextKeys() {
    }
}
