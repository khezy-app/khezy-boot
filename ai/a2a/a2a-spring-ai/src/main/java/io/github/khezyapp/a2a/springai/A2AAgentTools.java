package io.github.khezyapp.a2a.springai;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.core.client.RemoteAgent;
import io.github.khezyapp.a2a.core.client.RemoteSkill;
import io.github.khezyapp.a2a.core.client.SendOutcome;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;

/**
 * Agent-as-tool: coarse delegation to a whole agent by name, plus discovery.
 *
 * <p>Complements {@link A2ASkillToolFactory}: the model prefers a specific skill tool and
 * falls back to {@link #sendToAgent(String, String, ToolContext)} when the agent itself
 * should decide how to handle the work. Both paths share the connector's per-conversation
 * task resumption and the compact JSON formatter.</p>
 */
public final class A2AAgentTools {

    private final RemoteAgentRegistry registry;
    private final A2AConnector connector;
    private final A2AResultFormatter formatter;

    /**
     * Creates the agent tools.
     *
     * @param registry the remote agent registry
     * @param connector the conversation-aware connector
     * @param formatter the result formatter
     */
    public A2AAgentTools(final RemoteAgentRegistry registry, final A2AConnector connector,
                         final A2AResultFormatter formatter) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
    }

    /**
     * Delegates a task to one agent without selecting a skill.
     *
     * @param agentName the exact remote agent name
     * @param task the complete task description and context
     * @param toolContext the Spring AI tool context carrying the conversation id
     * @return the formatted outcome, or a structured JSON error
     */
    @Tool(description = """
            Delegate a task to a specific remote A2A agent by name. Prefer a specific skill \
            tool when one matches the request; use this agent-level tool when the agent itself \
            should decide how to handle the work or when no skill matches. When continuing a \
            previous exchange (for example answering a clarification), restate the new \
            information; the client automatically resumes the correct remote task.""")
    public String sendToAgent(
            @ToolParam(description = "The exact name of the remote agent. "
                    + "Call listRemoteAgents to discover the available names.")
            final String agentName,
            @ToolParam(description = "The complete task description and all relevant context "
                    + "for the remote agent.")
            final String task,
            final ToolContext toolContext) {
        final var agent = registry.find(agentName).orElse(null);
        if (agent == null) {
            return A2AToolErrors.unknownAgent(agentName, registry.agents());
        }
        final var conversationId = A2ASkillToolFactory.conversationId(toolContext);
        if (conversationId == null) {
            return A2AToolErrors.missingConversationId();
        }
        final List<Part<?>> parts = List.of(new TextPart(task == null ? "" : task));
        final SendOutcome outcome = connector.invoke(agent, parts, null, conversationId);
        return formatter.format(outcome);
    }

    /**
     * Lists the registered agents with their URLs, descriptions and skills.
     *
     * @return a human-readable list, or a short message when nothing is registered
     */
    @Tool(description = "List the remote A2A agents and the skills each one exposes. "
            + "Use this to discover where to delegate a request.")
    public String listRemoteAgents() {
        final var agents = registry.agents();
        if (agents.isEmpty()) {
            return "No remote agents are configured.";
        }
        return agents.stream()
                .map(A2AAgentTools::describeAgent)
                .collect(Collectors.joining("\n"));
    }

    private static String describeAgent(final RemoteAgent agent) {
        final var skills = agent.skills().stream()
                .map(A2AAgentTools::describeSkill)
                .collect(Collectors.joining(", "));
        return text(agent.name()) + " (" + text(agent.baseUrl()) + "): "
                + orDefault(agent.card().description(), "no description")
                + " | skills: " + (skills.isEmpty() ? "none" : skills);
    }

    private static String describeSkill(final RemoteSkill skill) {
        return text(skill.id()) + " - " + text(skill.name());
    }

    private static String orDefault(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(final String value) {
        return value == null ? "" : value;
    }
}
