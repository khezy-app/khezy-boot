package io.github.khezyapp.a2a.springai;

import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import org.springframework.ai.chat.client.ChatClient;

/** Handle one incoming message and return the agent's text reply. */
public interface ChatClientMessageHandler {

    String handle(ChatClient chatClient, AgentExecutionContext context);
}
