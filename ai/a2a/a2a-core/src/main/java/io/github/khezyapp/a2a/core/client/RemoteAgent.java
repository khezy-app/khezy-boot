package io.github.khezyapp.a2a.core.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/**
 * A resolved remote agent and the SDK client that talks to it.
 *
 * <p>The client is long-lived and owned by the registry that created this value. Callers
 * must not close it; doing so breaks every other conversation using the agent.</p>
 *
 * @param baseUrl the agent base URL the card was resolved from
 * @param card the SDK agent card
 * @param client the SDK client bound to the card
 */
public record RemoteAgent(String baseUrl, AgentCard card, Client client) {

    /**
     * Creates a remote agent.
     */
    public RemoteAgent {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(card, "card must not be null");
        Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Returns the agent name from the card.
     *
     * @return the agent name
     */
    public String name() {
        return card.name();
    }

    /**
     * Returns the card skills as remote skill views.
     *
     * @return an immutable list of skills
     */
    public List<RemoteSkill> skills() {
        final var skills = card.skills();
        if (Objects.isNull(skills)) {
            return List.of();
        }
        return skills.stream()
                .map(skill -> RemoteSkill.from(name(), baseUrl, skill))
                .toList();
    }

    /**
     * Returns the interface matching the card's preferred transport, or the first one.
     *
     * @return the selected interface, or empty when the card advertises none
     */
    public Optional<AgentInterface> selectedInterface() {
        final var interfaces = card.supportedInterfaces();
        if (Objects.isNull(interfaces) || interfaces.isEmpty()) {
            return Optional.empty();
        }
        final var preferred = card.preferredTransport();
        if (Objects.nonNull(preferred) && !preferred.isBlank()) {
            for (final var candidate : interfaces) {
                if (preferred.equals(candidate.protocolBinding())) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.of(interfaces.get(0));
    }

    /**
     * Returns the tenant of the selected interface.
     *
     * <p>The SDK tenant is a routing key, not an application persistence scope.</p>
     *
     * @return the tenant, or empty when the selected interface has none
     */
    public Optional<String> tenant() {
        return selectedInterface()
                .map(AgentInterface::tenant)
                .filter(tenant -> Objects.nonNull(tenant) && !tenant.isBlank());
    }
}
