package io.github.khezyapp.a2a.coredef.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCard;

import io.github.khezyapp.a2a.core.client.AgentCardResolver;
import io.github.khezyapp.a2a.core.client.ClientBuildOptions;
import io.github.khezyapp.a2a.core.client.RemoteAgent;
import io.github.khezyapp.a2a.core.client.RemoteSkill;

/**
 * Registry of resolved remote agents.
 *
 * <p>Registration is fail-soft: an unreachable or invalid card leaves the registry
 * unchanged. Lookups can be made by name (with a case-insensitive fallback) or by
 * skill id. The registry owns the SDK clients and closes them on
 * {@link #close()}.</p>
 */
public class RemoteAgentRegistry implements AutoCloseable {

    private final AgentCardResolver cardResolver;
    private final A2AClientFactory clientFactory;
    private final ClientBuildOptions buildOptions;
    private final ConcurrentMap<String, RemoteAgent> agents = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates an empty registry.
     *
     * @param cardResolver the card resolver
     * @param clientFactory the client factory
     * @param buildOptions the options used for every client
     */
    public RemoteAgentRegistry(final AgentCardResolver cardResolver, final A2AClientFactory clientFactory,
                               final ClientBuildOptions buildOptions) {
        this(cardResolver, clientFactory, buildOptions, List.of());
    }

    /**
     * Creates a registry and registers the configured URLs, ignoring failures.
     *
     * @param cardResolver the card resolver
     * @param clientFactory the client factory
     * @param buildOptions the options used for every client
     * @param urls the agent base URLs
     */
    public RemoteAgentRegistry(final AgentCardResolver cardResolver, final A2AClientFactory clientFactory,
                               final ClientBuildOptions buildOptions, final Collection<String> urls) {
        this.cardResolver = Objects.requireNonNull(cardResolver, "cardResolver must not be null");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
        this.buildOptions = Objects.requireNonNull(buildOptions, "buildOptions must not be null");
        registerAll(urls);
    }

    /**
     * Resolves and registers one agent.
     *
     * @param baseUrl the agent base URL
     * @return the registered agent, or empty when registration failed
     */
    public Optional<RemoteAgent> register(final String baseUrl) {
        if (closed.get() || baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        final AgentCard card;
        try {
            card = cardResolver.resolve(baseUrl).orElse(null);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (card == null) {
            return Optional.empty();
        }
        final Client client;
        try {
            client = clientFactory.create(card, buildOptions);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        final var agent = new RemoteAgent(baseUrl, card, client);
        final var previous = agents.put(baseUrl, agent);
        if (previous != null && previous.client() != client) {
            closeQuietly(previous.client());
        }
        return Optional.of(agent);
    }

    /**
     * Registers every URL, ignoring failures.
     *
     * @param urls the agent base URLs
     */
    public void registerAll(final Collection<String> urls) {
        if (urls == null) {
            return;
        }
        for (final String url : urls) {
            register(url);
        }
    }

    /**
     * Re-resolves every registered agent, keeping the old entry when a refresh fails.
     */
    public void refresh() {
        for (final var entry : List.copyOf(agents.entrySet())) {
            final String baseUrl = entry.getKey();
            if (!agents.containsKey(baseUrl)) {
                continue;
            }
            final AgentCard card;
            try {
                card = cardResolver.resolve(baseUrl).orElse(null);
            } catch (RuntimeException e) {
                continue;
            }
            if (card == null) {
                continue;
            }
            final Client client;
            try {
                client = clientFactory.create(card, buildOptions);
            } catch (RuntimeException e) {
                continue;
            }
            final var replacement = new RemoteAgent(baseUrl, card, client);
            final var previous = agents.put(baseUrl, replacement);
            if (previous != null && previous.client() != client) {
                closeQuietly(previous.client());
            }
        }
    }

    /**
     * Finds an agent by name or base URL.
     *
     * @param name the agent name or base URL
     * @return the agent, or empty when absent
     */
    public Optional<RemoteAgent> find(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (final var agent : agents.values()) {
            if (name.equals(agent.name()) || name.equals(agent.baseUrl())) {
                return Optional.of(agent);
            }
        }
        for (final var agent : agents.values()) {
            if (name.equalsIgnoreCase(agent.name()) || name.equalsIgnoreCase(agent.baseUrl())) {
                return Optional.of(agent);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the first agent advertising a skill.
     *
     * @param skillId the skill id
     * @return the agent, or empty when no agent advertises the skill
     */
    public Optional<RemoteAgent> findBySkillId(final String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        return agents.values().stream()
                .filter(agent -> agent.skills().stream().anyMatch(skill -> skillId.equals(skill.id())))
                .findFirst();
    }

    /**
     * Returns every registered skill.
     *
     * @return the skills
     */
    public List<RemoteSkill> skills() {
        final var skills = new ArrayList<RemoteSkill>();
        for (final var agent : agents.values()) {
            skills.addAll(agent.skills());
        }
        return List.copyOf(skills);
    }

    /**
     * Returns every registered agent.
     *
     * @return the agents
     */
    public List<RemoteAgent> agents() {
        return List.copyOf(agents.values());
    }

    /**
     * Removes and closes one agent.
     *
     * @param name the agent name or base URL
     * @return {@code true} when an agent was removed
     */
    public boolean unregister(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        final String key = matchingKey(name, false) != null
                ? matchingKey(name, false)
                : matchingKey(name, true);
        if (key == null) {
            return false;
        }
        final var removed = agents.remove(key);
        if (removed != null) {
            closeQuietly(removed.client());
            return true;
        }
        return false;
    }

    /**
     * Closes every SDK client. Calling this method more than once has no effect.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (final var agent : agents.values()) {
            closeQuietly(agent.client());
        }
        agents.clear();
    }

    private String matchingKey(final String name, final boolean ignoreCase) {
        for (final var entry : new LinkedHashMap<>(agents).entrySet()) {
            final var agent = entry.getValue();
            final boolean baseUrlMatch = ignoreCase
                    ? name.equalsIgnoreCase(agent.baseUrl())
                    : name.equals(agent.baseUrl());
            final boolean nameMatch = ignoreCase
                    ? name.equalsIgnoreCase(agent.name())
                    : name.equals(agent.name());
            if (baseUrlMatch || nameMatch) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void closeQuietly(final Client client) {
        try {
            client.close();
        } catch (RuntimeException e) {
            // A failing close must not prevent the registry from releasing the rest.
        }
    }
}
