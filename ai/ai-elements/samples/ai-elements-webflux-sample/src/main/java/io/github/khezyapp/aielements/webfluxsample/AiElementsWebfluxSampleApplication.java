package io.github.khezyapp.aielements.webfluxsample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable WebFlux demo exposing {@code POST /api/chat} as an ai-elements UI-message
 * stream over SSE, proving the {@code Flux<ServerSentEvent<String>>} transport of the
 * ai-elements Spring AI integration end to end. Runs in {@code mock} mode by default
 * and switches to live DeepSeek via the {@code deepseek} Spring profile.
 */
@SpringBootApplication
public class AiElementsWebfluxSampleApplication {

    private AiElementsWebfluxSampleApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(AiElementsWebfluxSampleApplication.class, args);
    }
}
