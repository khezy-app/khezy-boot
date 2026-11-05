package io.github.khezyapp.a2a.coredef.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;

import io.github.khezyapp.a2a.core.client.RemoteAgent;

/**
 * Shared fixtures for the core defaults tests.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    /**
     * Builds a card with one skill and one JSON-RPC interface.
     *
     * @param name the agent name
     * @param url the interface URL
     * @param tenant the interface tenant
     * @param skillId the skill id
     * @param streaming whether streaming is advertised
     * @return the card
     */
    public static AgentCard card(final String name, final String url, final String tenant,
                                 final String skillId, final boolean streaming) {
        final var skill = AgentSkill.builder()
                .id(skillId)
                .name(skillId)
                .description("test skill")
                .tags(List.of("test"))
                .build();
        return AgentCard.builder()
                .name(name)
                .description("test agent")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().streaming(streaming).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(skill))
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", url, tenant, "1.0")))
                .preferredTransport("JSONRPC")
                .build();
    }

    /**
     * Builds a remote agent.
     *
     * @param name the agent name
     * @param url the base URL
     * @param tenant the interface tenant
     * @param skillId the skill id
     * @param streaming whether streaming is advertised
     * @param client the SDK client
     * @return the remote agent
     */
    public static RemoteAgent agent(final String name, final String url, final String tenant,
                                    final String skillId, final boolean streaming, final Client client) {
        return new RemoteAgent(url, card(name, url, tenant, skillId, streaming), client);
    }

    /**
     * A clock whose instant can be advanced by tests.
     */
    public static final class MutableClock extends Clock {

        private Instant instant;

        /**
         * Creates a clock at the given instant.
         *
         * @param instant the initial instant
         */
        public MutableClock(final Instant instant) {
            this.instant = instant;
        }

        /**
         * Advances the clock.
         *
         * @param duration the amount to advance
         */
        public void advance(final Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
