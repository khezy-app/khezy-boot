package io.github.khezyapp.a2a.springai;

import io.github.khezyapp.a2a.core.executor.AgentEventSink;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import org.springframework.ai.chat.client.ChatClient;

/** Handle one incoming message, emitting incremental results onto the sink. */
public interface StreamingChatClientMessageHandler {

    void handle(ChatClient chatClient, AgentExecutionContext context, AgentEventSink sink);
}
