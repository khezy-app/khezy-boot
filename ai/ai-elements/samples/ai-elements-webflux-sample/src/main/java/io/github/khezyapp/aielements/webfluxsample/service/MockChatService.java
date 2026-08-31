package io.github.khezyapp.aielements.webfluxsample.service;

import io.github.khezyapp.aielements.model.request.ChatRequest;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Default ({@code mock}) chat backend. Streams a canned sequence of Spring AI
 * {@link ChatResponse} deltas so the reactive transport can be exercised end to end
 * without a model API key. The generated events exercise {@code start}, {@code start-step},
 * {@code text-*} and the {@code finish} sequence through the real converter pipeline.
 */
@Service
@Profile("mock")
public class MockChatService implements ChatService {

    @Override
    public Flux<ChatResponse> stream(final ChatRequest request) {
        final var deltaA = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Hello from "))));
        final var deltaB = new ChatResponse(
                List.of(new Generation(new AssistantMessage("WebFlux!"))));
        final var finish = new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage(""),
                        ChatGenerationMetadata.builder().finishReason("stop").build())),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(10, 5, 15))
                        .build());
        return Flux.just(deltaA, deltaB, finish);
    }
}
