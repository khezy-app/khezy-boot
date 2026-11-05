package io.github.khezyapp.a2a.sampleserver;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.khezyapp.a2a.starter.A2AServerProperties;

/**
 * Publishes the sample's agent card as an explicit bean.
 *
 * <p>Name, description, version and URL come from
 * {@code io.github.khezyapp.a2a.server.agent-card.*}; the four arithmetic skills are part
 * of the sample's business surface.</p>
 */
@Configuration(proxyBeanMethods = false)
public class MathAgentCardConfiguration {

    private static final String JSONRPC = TransportProtocol.JSONRPC.asString();
    private static final List<String> TEXT = List.of("text");

    /**
     * Creates the sample agent card.
     *
     * @param properties the starter properties carrying the card text and URL
     * @return the agent card
     */
    @Bean
    public AgentCard mathAgentCard(final A2AServerProperties properties) {
        final var configured = properties.getAgentCard();
        final var url = configured.getUrl();
        return AgentCard.builder()
                .name(configured.getName())
                .description(configured.getDescription())
                .version(configured.getVersion())
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(TEXT)
                .defaultOutputModes(TEXT)
                .skills(List.of(
                        skill("add", "Add", "Adds two integers", "2 + 3"),
                        skill("subtract", "Subtract", "Subtracts two integers", "12 - 2"),
                        skill("multiply", "Multiply", "Multiplies two integers", "7 * 6"),
                        skill("divide", "Divide", "Divides two integers", "16 / 4")))
                .supportedInterfaces(List.of(new AgentInterface(JSONRPC, url)))
                .url(url)
                .preferredTransport(JSONRPC)
                .build();
    }

    private static AgentSkill skill(final String id,
                                    final String name,
                                    final String description,
                                    final String example) {
        return AgentSkill.builder()
                .id(id)
                .name(name)
                .description(description)
                .tags(List.of("math", "arithmetic"))
                .examples(List.of(example))
                .inputModes(TEXT)
                .outputModes(TEXT)
                .build();
    }
}
