package io.github.khezyapp.aielements.webfluxsample.service;

import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.springai.convert.ChatRequestConverter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Live {@link ChatService} used under the {@code deepseek} profile. Converts the
 * ai-elements {@link ChatRequest} into Spring AI messages and streams the raw
 * {@link ChatResponse} deltas, which the controller pipes through the ai-elements
 * WebFlux SSE writer.
 */
@Service
@Profile("deepseek")
public class DeepSeekChatService implements ChatService {

    private final ChatClient chatClient;

    public DeepSeekChatService(final ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<ChatResponse> stream(final ChatRequest request) {
        final var messages = ChatRequestConverter.toSpringAiMessages(request);
        return chatClient.prompt().messages(messages).stream().chatResponse();
    }
}
