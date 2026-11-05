package io.github.khezyapp.a2a.core.client;

/**
 * Remembers the A2A identifiers a conversation needs for its next turn.
 *
 * <p>The host owns the conversation id (for example, a chat session id) and passes it
 * out-of-band. A2A ids never reach the language model. Implementations must be safe for
 * concurrent use: {@link A2AConnector} serializes calls per conversation, but different
 * conversations may be recorded in parallel.</p>
 */
public interface A2AConversationStore {

    /**
     * Returns the ids stored for a conversation, agent, and skill.
     *
     * <p>Implementations return {@link TaskRef#empty()} rather than {@code null} when
     * nothing is stored.</p>
     *
     * @param conversationId the host conversation id
     * @param agentName the agent name
     * @param skillId the skill id, or an empty string when no skill was selected
     * @return the stored reference
     */
    TaskRef ref(String conversationId, String agentName, String skillId);

    /**
     * Records an interrupted task, keeping both ids for the next turn.
     *
     * @param conversationId the host conversation id
     * @param agentName the agent name
     * @param skillId the skill id, or an empty string when no skill was selected
     * @param contextId the A2A context id, or {@code null}
     * @param taskId the A2A task id, or {@code null}
     */
    void recordInterrupted(String conversationId, String agentName, String skillId,
                           String contextId, String taskId);

    /**
     * Records a pending task, keeping both ids for a later poll or continuation.
     *
     * @param conversationId the host conversation id
     * @param agentName the agent name
     * @param skillId the skill id, or an empty string when no skill was selected
     * @param contextId the A2A context id, or {@code null}
     * @param taskId the A2A task id, or {@code null}
     */
    void recordPending(String conversationId, String agentName, String skillId,
                       String contextId, String taskId);

    /**
     * Records a settled task, keeping the context and dropping the task id.
     *
     * @param conversationId the host conversation id
     * @param agentName the agent name
     * @param skillId the skill id, or an empty string when no skill was selected
     * @param contextId the A2A context id, or {@code null}
     */
    void recordSettled(String conversationId, String agentName, String skillId, String contextId);

    /**
     * Removes every entry for a conversation.
     *
     * @param conversationId the host conversation id
     */
    void clearConversation(String conversationId);

    /**
     * Removes every entry.
     */
    default void clear() {
    }
}
