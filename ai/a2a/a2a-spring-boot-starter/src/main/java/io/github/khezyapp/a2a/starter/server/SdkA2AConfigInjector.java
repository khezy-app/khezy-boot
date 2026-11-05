package io.github.khezyapp.a2a.starter.server;

import java.lang.reflect.Field;
import java.util.Objects;

import org.a2aproject.sdk.server.FixedInstance;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;

/**
 * Injects Spring beans into SDK fields that the programmatic constructors do not expose.
 *
 * <p>The SDK {@code DefaultRequestHandler} reads its configuration during
 * {@code @PostConstruct initConfig()} through a package-private {@code configProvider}
 * field; the programmatic builder never sets it. The same initialization step also
 * resolves the authorization provider and the tenant executor router from CDI
 * {@link Instance} fields, overwriting the builder values. The {@code JSONRPCHandler}
 * programmatic constructor has no {@link AgentCardRouter} argument at all.</p>
 *
 * <p>All reflection in the starter is confined to this class. Every lookup fails fast
 * with the SDK class and field name when the pinned SDK version changes shape.</p>
 */
public final class SdkA2AConfigInjector {

    /** Package-private SDK field read by {@code initConfig()}. */
    public static final String CONFIG_PROVIDER_FIELD = "configProvider";

    /** Private SDK field holding the CDI authorization provider instance. */
    public static final String AUTHORIZATION_PROVIDER_INSTANCE_FIELD = "authorizationProviderInstance";

    /** Private SDK field holding the resolved authorization provider. */
    public static final String AUTHORIZATION_PROVIDER_FIELD = "authorizationProvider";

    /** Private SDK field holding the CDI agent executor router instance. */
    public static final String AGENT_EXECUTOR_ROUTER_INSTANCE_FIELD = "agentExecutorRouterInstance";

    /** Private SDK field holding the resolved agent executor router. */
    public static final String AGENT_EXECUTOR_ROUTER_FIELD = "agentExecutorRouter";

    /** Private SDK field holding the tenant-aware card router. */
    public static final String AGENT_CARD_ROUTER_FIELD = "agentCardRouter";

    private SdkA2AConfigInjector() {
    }

    /**
     * Sets the configuration provider used by {@code initConfig()}.
     *
     * <p>Must run before Spring invokes {@code @PostConstruct} on the handler bean.</p>
     *
     * @param handler the SDK request handler
     * @param provider the Spring-backed configuration provider
     */
    public static void setConfigProvider(final DefaultRequestHandler handler, final A2AConfigProvider provider) {
        setField(handler, DefaultRequestHandler.class, CONFIG_PROVIDER_FIELD,
                Objects.requireNonNull(provider, "provider must not be null"));
    }

    /**
     * Sets the task authorization provider so {@code initConfig()} keeps it.
     *
     * @param handler the SDK request handler
     * @param provider the authorization provider
     */
    public static void setAuthorizationProvider(final DefaultRequestHandler handler,
                                                final TaskAuthorizationProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        setField(handler, DefaultRequestHandler.class, AUTHORIZATION_PROVIDER_INSTANCE_FIELD,
                new FixedInstance<>(provider));
        setField(handler, DefaultRequestHandler.class, AUTHORIZATION_PROVIDER_FIELD, provider);
    }

    /**
     * Sets the tenant executor router so {@code initConfig()} keeps it.
     *
     * @param handler the SDK request handler
     * @param router the executor router
     */
    public static void setAgentExecutorRouter(final DefaultRequestHandler handler,
                                              final AgentExecutorRouter router) {
        Objects.requireNonNull(router, "router must not be null");
        setField(handler, DefaultRequestHandler.class, AGENT_EXECUTOR_ROUTER_INSTANCE_FIELD,
                new FixedInstance<>(router));
        setField(handler, DefaultRequestHandler.class, AGENT_EXECUTOR_ROUTER_FIELD, router);
    }

    /**
     * Sets the tenant-aware card router on the JSON-RPC handler.
     *
     * @param handler the JSON-RPC handler
     * @param router the card router
     */
    public static void setAgentCardRouter(final JSONRPCHandler handler, final AgentCardRouter router) {
        setField(handler, JSONRPCHandler.class, AGENT_CARD_ROUTER_FIELD,
                Objects.requireNonNull(router, "router must not be null"));
    }

    /**
     * Returns whether the given class declares an injectable SDK field.
     *
     * @param type the SDK class
     * @param name the field name
     * @return {@code true} when the field exists on the class hierarchy
     */
    public static boolean hasField(final Class<?> type, final String name) {
        return findField(type, name) != null;
    }

    private static void setField(final Object target, final Class<?> type, final String name, final Object value) {
        Objects.requireNonNull(target, "target must not be null");
        final var field = findField(type, name);
        if (field == null) {
            throw new IllegalStateException("A2A SDK field '" + name + "' not found on "
                    + type.getName() + ". The pinned SDK version changed its shape; review "
                    + SdkA2AConfigInjector.class.getSimpleName() + ".");
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Could not set A2A SDK field '" + name + "' on "
                    + type.getName(), e);
        }
    }

    private static Field findField(final Class<?> type, final String name) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (final NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
