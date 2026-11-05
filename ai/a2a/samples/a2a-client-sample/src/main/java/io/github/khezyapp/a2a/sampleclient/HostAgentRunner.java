package io.github.khezyapp.a2a.sampleclient;

import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.core.client.SendOutcome;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;
import io.github.khezyapp.a2a.starter.A2AClientProperties;

/**
 * Host-agent validation loop: discover the remote agent card, send {@code "2 + 3"}, then
 * demonstrate a multi-turn {@code INPUT_REQUIRED} resume through {@link A2AConnector}.
 *
 * <p>Failures are logged, never thrown — a sample must not crash the boot process; read
 * the ERROR line to see what went wrong.</p>
 */
@Component
public class HostAgentRunner implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(HostAgentRunner.class);

    private static final String DEFAULT_URL = "http://localhost:8080/a2a";
    private static final String ADD_SKILL = "add";
    private static final String ADD_CONVERSATION = "sample-add";
    private static final String MULTI_TURN_CONVERSATION = "sample-multiturn";

    private final RemoteAgentRegistry registry;
    private final A2AConnector connector;
    private final A2AClientProperties properties;

    /**
     * Creates the runner.
     *
     * @param registry the agent registry
     * @param connector the multi-turn connector
     * @param properties the client properties
     */
    public HostAgentRunner(final RemoteAgentRegistry registry,
                           final A2AConnector connector,
                           final A2AClientProperties properties) {
        this.registry = registry;
        this.connector = connector;
        this.properties = properties;
    }

    @Override
    public void run(final String... args) {
        final var url = properties.getUrls().isEmpty() ? DEFAULT_URL : properties.getUrls().get(0);
        try {
            final var agent = registry.register(url)
                    .or(() -> registry.find(url))
                    .orElse(null);
            if (Objects.isNull(agent)) {
                LOG.error("Could not discover an agent at {} — is the server sample running?", url);
                return;
            }
            LOG.info("Discovered remote agent '{}' v{} at {}",
                    agent.name(), agent.card().version(), agent.baseUrl());

            final var outcome = connector.invoke(agent, List.of(new TextPart("2 + 3")), ADD_SKILL, ADD_CONVERSATION);
            LOG.info("2 + 3 -> {}", describe(outcome));

            final var firstTurn = connector.invoke(agent, List.of(new TextPart("2 +")),
                    ADD_SKILL, MULTI_TURN_CONVERSATION);
            if (!firstTurn.isInterrupted()) {
                LOG.info("Expected INPUT_REQUIRED for '2 +' but got: {}", describe(firstTurn));
                return;
            }
            LOG.info("Agent asks: {}", firstTurn.message());
            final var resumed = connector.invoke(agent, List.of(new TextPart("3")),
                    ADD_SKILL, MULTI_TURN_CONVERSATION);
            LOG.info("2 + 3 (resumed) -> {}", describe(resumed));
        } catch (final RuntimeException e) {
            LOG.error("A2A host agent call failed for {}: {}", url, e.getMessage(), e);
        }
    }

    private static String describe(final SendOutcome outcome) {
        if (outcome.isError()) {
            return "ERROR " + outcome.error();
        }
        final var artifacts = outcome.artifacts().isEmpty()
                ? ""
                : " artifacts=" + outcome.artifacts();
        final var state = outcome.state() == null ? "MESSAGE" : outcome.state().toString();
        return state + " \"" + outcome.message() + "\"" + artifacts;
    }
}
