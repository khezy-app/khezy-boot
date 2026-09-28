package io.github.khezyapp.aielements.sample.service;

import io.github.khezyapp.aielements.model.request.ChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Streams a {@link ChatRequest} into Spring AI {@link ChatResponse} deltas. The
 * {@code deepseek} profile provides a live implementation backed by the DeepSeek
 * {@code ChatClient}; the {@code mock} profile does not provide one (it replays a
 * canned SSE fixture instead).
 */
public interface ChatService {

    Flux<ChatResponse> stream(ChatRequest request);
}
