package io.github.khezyapp.aielements.sample.controller;

import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.sample.service.ChatService;
import io.github.khezyapp.aielements.sample.service.MockChatService;
import io.github.khezyapp.aielements.springai.sse.AiElementsSse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * Exposes {@code POST /api/chat} as an ai-elements UI-message stream over SSE.
 *
 * <p>Exactly one of the two backends is wired per active profile: under {@code mock}
 * the canned {@link io.github.khezyapp.aielements.model.response.SseEvent} fixture is written directly;
 * under {@code deepseek} the live {@link io.github.khezyapp.aielements.springai.convert.ChatResponseStreamConverter}
 * pipeline is streamed.</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final ChatService chatService;
    private final MockChatService mockChatService;

    public ChatController(
            @Autowired(required = false) final ChatService chatService,
            @Autowired(required = false) final MockChatService mockChatService
    ) {
        this.chatService = chatService;
        this.mockChatService = mockChatService;
    }

    @PostMapping(produces = "text/event-stream")
    public SseEmitter chat(final @RequestBody ChatRequest request,
                           final HttpServletResponse response) {
        AiElementsSse.applyStreamHeaders(response);
        if (Objects.nonNull(mockChatService)) {
            return AiElementsSse.writeTo(new SseEmitter(STREAM_TIMEOUT_MILLIS), mockChatService.events());
        }
        return AiElementsSse.streamToEmitter(chatService.stream(request), STREAM_TIMEOUT_MILLIS);
    }
}
