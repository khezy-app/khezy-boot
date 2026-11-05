package io.github.khezyapp.a2a.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.khezyapp.a2a.core.server.A2ACallContextFactory;
import io.github.khezyapp.a2a.starter.A2AServerProperties;
import io.github.khezyapp.a2a.starter.server.A2AServerAutoConfiguration;

/**
 * HTTP transport for the A2A server: JSON-RPC, SSE and the agent card.
 *
 * <p>Active only when the server auto-configuration produced a {@link JSONRPCHandler}.
 * Spring Security is optional: when present, the servlet call-context factory maps the
 * security context to the SDK user; otherwise every request is unauthenticated.</p>
 */
@AutoConfiguration(after = A2AServerAutoConfiguration.class)
@ConditionalOnClass({DefaultRequestHandler.class, JSONRPCHandler.class})
@ConditionalOnBean(JSONRPCHandler.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = A2AServerProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(A2AServerProperties.class)
public class A2AWebAutoConfiguration {

    /**
     * Creates the servlet transport adapter.
     *
     * @param jsonRpcHandler the SDK JSON-RPC handler
     * @return the servlet adapter
     */
    @Bean
    @ConditionalOnMissingBean
    public ServletA2AServerJsonRpcHandler servletA2AServerJsonRpcHandler(final JSONRPCHandler jsonRpcHandler) {
        return new ServletA2AServerJsonRpcHandler(jsonRpcHandler);
    }

    /**
     * Creates the JSON-RPC controller.
     *
     * @param handler the servlet JSON-RPC adapter
     * @param callContextFactory the call-context factory
     * @return the controller
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AJsonRpcController a2aJsonRpcController(
            final ServletA2AServerJsonRpcHandler handler,
            final A2ACallContextFactory<HttpServletRequest> callContextFactory) {
        return new A2AJsonRpcController(handler, callContextFactory);
    }

    /**
     * Creates the agent card controller.
     *
     * @param properties the server properties
     * @param agentCard the default agent card
     * @param cardRouters the optional tenant card router
     * @return the controller
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentCardController agentCardController(
            final A2AServerProperties properties,
            final AgentCard agentCard,
            final ObjectProvider<AgentCardRouter> cardRouters) {
        return new AgentCardController(properties, agentCard, cardRouters);
    }

    /** Call-context configuration with Spring Security on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SpringSecurityCallContextConfiguration {

        @Bean
        @ConditionalOnMissingBean
        A2ACallContextFactory<HttpServletRequest> servletA2ACallContextFactory(final A2AServerProperties properties) {
            return new ServletA2ACallContextFactory(properties, new SpringSecurityUserResolver());
        }
    }

    /** Call-context configuration without Spring Security on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
    static class PlainCallContextConfiguration {

        @Bean
        @ConditionalOnMissingBean
        A2ACallContextFactory<HttpServletRequest> servletA2ACallContextFactory(final A2AServerProperties properties) {
            return new ServletA2ACallContextFactory(properties);
        }
    }
}
