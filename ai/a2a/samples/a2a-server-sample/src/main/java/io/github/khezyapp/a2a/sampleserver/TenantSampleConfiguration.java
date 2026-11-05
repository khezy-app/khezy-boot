package io.github.khezyapp.a2a.sampleserver;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.github.khezyapp.a2a.starter.server.MapAgentCardRouter;
import io.github.khezyapp.a2a.starter.server.MapAgentExecutorRouter;

/**
 * Opt-in SDK 1.4 multi-tenancy demo ({@code --spring.profiles.active=tenant}).
 *
 * <p>The default {@link MathAgentExecutor} serves requests without a tenant. Calls
 * carrying tenant {@value #TENANT} are routed to a second executor and can discover a
 * tenant-specific card at {@code /a2a/.well-known/acme/agent-card.json}.</p>
 */
@Profile(TenantSampleConfiguration.TENANT_PROFILE)
@Configuration(proxyBeanMethods = false)
public class TenantSampleConfiguration {

    /** Profile that enables the tenant demo. */
    public static final String TENANT_PROFILE = "tenant";

    /** Tenant id used by the demo. */
    public static final String TENANT = "acme";

    private static final String JSONRPC = TransportProtocol.JSONRPC.asString();

    /**
     * Tenant-specific executor (the same math logic under a different tenant).
     *
     * @return the tenant executor
     */
    @Bean
    public AgentExecutor acmeMathAgentExecutor() {
        return new MathAgentExecutor();
    }

    /**
     * Routes tenant {@value #TENANT} to its executor and everything else to the default.
     *
     * @param defaultExecutor the component-scanned default executor
     * @param acmeExecutor the tenant executor
     * @return the executor router
     */
    @Bean
    public AgentExecutorRouter agentExecutorRouter(@Qualifier("mathAgentExecutor")
                                                   final MathAgentExecutor defaultExecutor,
                                                   @Qualifier("acmeMathAgentExecutor")
                                                   final AgentExecutor acmeExecutor) {
        return new MapAgentExecutorRouter(defaultExecutor, Map.of(TENANT, acmeExecutor));
    }

    /**
     * Publishes a tenant card advertising the tenant on its JSON-RPC interface.
     *
     * @param defaultCard the default public card
     * @return the card router
     */
    @Bean
    public AgentCardRouter agentCardRouter(final AgentCard defaultCard) {
        final var tenantCard = AgentCard.builder(defaultCard)
                .name(defaultCard.name() + " [" + TENANT + "]")
                .supportedInterfaces(List.of(new AgentInterface(JSONRPC, defaultCard.url(), TENANT)))
                .build();
        return new MapAgentCardRouter(defaultCard, Map.of(TENANT, tenantCard));
    }
}
