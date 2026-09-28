package io.github.khezyapp.aielements.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable servlet demo exposing {@code POST /api/chat} as an ai-elements UI-message
 * stream over SSE. Runs in {@code mock} mode by default and switches to live DeepSeek
 * via the {@code deepseek} Spring profile.
 */
@SpringBootApplication
public class AiElementsSampleApplication {

    private AiElementsSampleApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(AiElementsSampleApplication.class, args);
    }
}
