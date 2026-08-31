package io.github.khezyapp.aielements.webfluxsample.controller;

import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.springai.sse.AiElementsSse;
import io.github.khezyapp.aielements.webfluxsample.service.ChatService;
import io.github.khezyapp.aielements.webfluxsample.service.MockChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Exposes {@code POST /api/chat} as an ai-elements UI-message stream over SSE using the
 * reactive (WebFlux) transport. Exactly one of the two backends is wired per active
 * profile: under {@code mock} a canned {@link reactor.core.publisher.Flux}{@code <}ChatResponse{@code >}
 * fixture is piped through {@link AiElementsSse#streamChat}; under {@code deepseek} the
 * live Spring AI streaming pipeline is used.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String NO_CACHE = "no-cache";
    private static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";
    private static final String X_VERCEL_AI_UI = "X-Vercel-AI-UI-Message-Stream";
    private static final String UI_STREAM_VERSION = "v1";

    private final ChatService chatService;
    private final MockChatService mockChatService;

    public ChatController(
            @Autowired(required = false) final ChatService chatService,
            @Autowired(required = false) final MockChatService mockChatService
    ) {
        this.chatService = chatService;
        this.mockChatService = mockChatService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(final @RequestBody ChatRequest request,
                                              final ServerHttpResponse response) {
        applyStreamHeaders(response);
        if (mockChatService != null) {
            return AiElementsSse.streamChat(mockChatService.stream(request));
        }
        return AiElementsSse.streamChat(chatService.stream(request));
    }

    private static void applyStreamHeaders(final ServerHttpResponse response) {
        response.getHeaders().setCacheControl(NO_CACHE);
        response.getHeaders().set(X_ACCEL_BUFFERING, "no");
        response.getHeaders().set(X_VERCEL_AI_UI, UI_STREAM_VERSION);
        response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
    }
}
