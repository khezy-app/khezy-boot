package io.github.khezyapp.a2a.springai;

/**
 * Keys the host passes to the remote-agent tools through Spring AI's
 * {@code ToolContext}.
 *
 * <p>The host supplies the conversation id when it builds the prompt; A2A
 * {@code contextId}/{@code taskId} correlation therefore never reaches the model:</p>
 *
 * <pre>{@code
 * chatClient.prompt()
 *         .user(text)
 *         .toolContext(Map.of(A2AToolContextKeys.CONVERSATION_ID, conversationId))
 *         .call();
 * }</pre>
 */
public final class A2AToolContextKeys {

    /**
     * Host conversation id (for example a chat session id) used to resume the
     * correct remote task.
     */
    public static final String CONVERSATION_ID = "conversationId";

    private A2AToolContextKeys() {
    }
}
