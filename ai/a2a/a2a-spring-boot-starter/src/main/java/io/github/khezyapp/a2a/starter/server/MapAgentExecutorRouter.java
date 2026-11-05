package io.github.khezyapp.a2a.starter.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;

/**
 * Simple tenant router over a map of {@link AgentExecutor} beans.
 *
 * <p>Blank, {@code null} and unknown tenants fall back to the default executor, matching
 * the SDK contract. The map is copied defensively.</p>
 */
public final class MapAgentExecutorRouter implements AgentExecutorRouter {

    private final AgentExecutor defaultExecutor;
    private final Map<String, AgentExecutor> executors;

    /**
     * Creates a router.
     *
     * @param defaultExecutor the executor used for blank or unknown tenants
     * @param executors tenant-specific executors keyed by tenant id
     */
    public MapAgentExecutorRouter(final AgentExecutor defaultExecutor,
                                  final Map<String, AgentExecutor> executors) {
        this.defaultExecutor = Objects.requireNonNull(defaultExecutor, "defaultExecutor must not be null");
        this.executors = copy(executors);
    }

    @Override
    public AgentExecutor resolve(final String tenant) {
        if (Objects.isNull(tenant) || tenant.isBlank()) {
            return defaultExecutor;
        }
        return executors.getOrDefault(tenant, defaultExecutor);
    }

    private static Map<String, AgentExecutor> copy(final Map<String, AgentExecutor> source) {
        if (Objects.isNull(source) || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
