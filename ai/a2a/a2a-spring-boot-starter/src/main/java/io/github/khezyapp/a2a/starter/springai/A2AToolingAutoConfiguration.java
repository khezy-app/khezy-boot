package io.github.khezyapp.a2a.starter.springai;

import java.util.ArrayList;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;
import io.github.khezyapp.a2a.springai.A2AAgentTools;
import io.github.khezyapp.a2a.springai.A2APromptBuilder;
import io.github.khezyapp.a2a.springai.A2APromptProvider;
import io.github.khezyapp.a2a.springai.A2AResultFormatter;
import io.github.khezyapp.a2a.springai.A2ASkillToolFactory;
import io.github.khezyapp.a2a.starter.A2AClientProperties;
import io.github.khezyapp.a2a.starter.client.A2AClientAutoConfiguration;

/**
 * Exposes the discovered remote agents and skills as Spring AI tools.
 *
 * <p>Active only when Spring AI's {@link ChatClient} and the optional
 * {@code a2a-spring-ai} module are both on the classpath, and only when the A2A client is
 * enabled. Beans are created eagerly so they can be injected into a workflow, but
 * nothing is attached to an auto-configured {@link ChatClient} unless
 * {@code io.github.khezyapp.a2a.client.attach-tools-to-chat-client=true} — the host
 * decides which tools to expose and when.</p>
 */
@AutoConfiguration(after = A2AClientAutoConfiguration.class)
@ConditionalOnClass({ChatClient.class, A2ASkillToolFactory.class})
@ConditionalOnProperty(prefix = A2AClientProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class A2AToolingAutoConfiguration {

    /**
     * Creates the result formatter used by every tool.
     *
     * @return the formatter
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AResultFormatter a2aResultFormatter() {
        return new A2AResultFormatter();
    }

    /**
     * Creates the skill-as-tool factory.
     *
     * @param registry the remote agent registry
     * @param connector the conversation-aware connector
     * @param formatter the result formatter
     * @return the skill tool factory
     */
    @Bean
    @ConditionalOnMissingBean
    public A2ASkillToolFactory a2aSkillToolFactory(final RemoteAgentRegistry registry,
                                                   final A2AConnector connector,
                                                   final A2AResultFormatter formatter) {
        return new A2ASkillToolFactory(registry, connector, formatter);
    }

    /**
     * Creates the agent-as-tool bundle.
     *
     * @param registry the remote agent registry
     * @param connector the conversation-aware connector
     * @param formatter the result formatter
     * @return the agent tools
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AAgentTools a2aAgentTools(final RemoteAgentRegistry registry,
                                       final A2AConnector connector,
                                       final A2AResultFormatter formatter) {
        return new A2AAgentTools(registry, connector, formatter);
    }

    /**
     * Creates the routing prompt provider.
     *
     * @return the default prompt builder
     */
    @Bean
    @ConditionalOnMissingBean
    public A2APromptProvider a2aPromptProvider() {
        return new A2APromptBuilder();
    }

    /**
     * Attaches the A2A tools and routing prompt to every auto-configured
     * {@link ChatClient}.
     *
     * <p>Registered only when
     * {@code io.github.khezyapp.a2a.client.attach-tools-to-chat-client=true}.</p>
     *
     * @param skillToolFactory the skill tools
     * @param agentTools the agent tools
     * @param registry the remote agent registry
     * @param promptProvider the routing prompt provider
     * @return the builder customizer
     */
    @Bean
    @ConditionalOnMissingBean(name = "a2aChatClientToolCustomizer")
    @ConditionalOnProperty(prefix = A2AClientProperties.PREFIX,
            name = "attach-tools-to-chat-client", havingValue = "true")
    public ChatClientBuilderCustomizer a2aChatClientToolCustomizer(
            final A2ASkillToolFactory skillToolFactory,
            final A2AAgentTools agentTools,
            final RemoteAgentRegistry registry,
            final A2APromptProvider promptProvider) {
        final var systemPrompt = promptProvider.build(registry.skills());
        final var tools = new ArrayList<Object>();
        tools.add(agentTools);
        tools.addAll(skillToolFactory.toolCallbacks());
        return builder -> builder.defaultSystem(systemPrompt).defaultTools(tools.toArray());
    }
}
