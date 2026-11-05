package io.github.khezyapp.a2a.springai;

import java.util.List;
import java.util.stream.Collectors;

import io.github.khezyapp.a2a.core.client.RemoteAgent;

/**
 * Structured JSON errors returned to the model instead of throwing.
 *
 * <p>Tool failures must reach the model as data so it can correct itself (pick another
 * agent, supply a conversation id) rather than abort the chat turn.</p>
 */
final class A2AToolErrors {

    private A2AToolErrors() {
    }

    /**
     * Builds the unknown-agent error, listing the agents the host actually knows.
     *
     * @param agentName the requested agent name
     * @param agents the registered agents
     * @return the JSON error document
     */
    static String unknownAgent(final String agentName, final List<RemoteAgent> agents) {
        final var names = agents == null
                ? "[]"
                : agents.stream()
                        .map(agent -> A2AResultFormatter.jsonString(agent.name()))
                        .collect(Collectors.joining(",", "[", "]"));
        return "{\"error\":" + A2AResultFormatter.jsonString("Unknown agent '" + agentName + "'.")
                + ",\"availableAgents\":" + names + '}';
    }

    /**
     * Builds the missing-conversation-id error.
     *
     * @return the JSON error document
     */
    static String missingConversationId() {
        return "{\"error\":" + A2AResultFormatter.jsonString(
                "Missing ToolContext conversationId. The host must pass "
                        + A2AToolContextKeys.CONVERSATION_ID + " in the Spring AI ToolContext.")
                + '}';
    }
}
