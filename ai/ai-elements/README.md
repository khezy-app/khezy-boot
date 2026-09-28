# KHEZY AI Elements — Vercel UI-message-stream for Spring AI

The KHEZY AI Elements layer turns a Spring AI `ChatClient` stream into the
**Vercel UI-message-stream** wire format — the SSE event set (`start`, `start-step`,
`reasoning-*`, `tool-input-start`, `text-*`, `finish-step`, `finish`, `[DONE]`) that
frontends like the ai-elements Vue SDK consume out of the box. It sits **on top of**
Spring AI, not instead of it: you still write your `ChatClient`, your prompts, and your
tool calls the Spring AI way. What we add is the piece Spring leaves open — formatting
the model's reactive `ChatResponse` stream into the protocol bytes the UI expects.

## Modules

| Module | Coordinates | What you get |
|---|---|---|
| [`ai-elements-model`](ai-elements-model/) | `io.github.khezyapp:ai-elements-model:1.0.0` | Request DTOs (`ChatRequest`) + the `SseEvent` wire model, written once against the shared Jackson 2.x annotations |
| [`ai-elements-spring-ai`](ai-elements-spring-ai/) | `io.github.khezyapp:ai-elements-spring-ai:1.0.0` | Converts Spring AI `ChatResponse`/request into the event stream, and writes it over servlet `SseEmitter` / reactive transports |
| [`samples/ai-elements-sample`](samples/ai-elements-sample/) | `0.0.1-SNAPSHOT` | Runnable **servlet** app: `POST /api/chat` returning the SSE bytes — mock-first, then live DeepSeek via a profile |
| [`samples/ai-elements-webflux-sample`](samples/ai-elements-webflux-sample/) | `0.0.1-SNAPSHOT` | Runnable **WebFlux** app on Netty: `POST /api/chat` returning `Flux<ServerSentEvent<String>>` via `AiElementsSse.streamChat(...)` — mock-first, then live DeepSeek via a profile |

Baseline: Spring AI `2.0.1`, Spring Boot `4.1.0`, Java 17.

## Quick start

Add the spring-ai module and Point-A providers you need, build a `ChatClient`, and
stream:

```groovy
dependencies {
    implementation "io.github.khezyapp:ai-elements-spring-ai:1.0.0"
    implementation "org.springframework.ai:spring-ai-starter-model-deepseek" // any provider
}
```

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    @PostMapping(produces = "text/event-stream")
    public SseEmitter chat(@RequestBody final ChatRequest request, final HttpServletResponse response) {
        AiElementsSse.applyStreamHeaders(response);
        final var messages = ChatRequestConverter.toSpringAiMessages(request);
        final var events = chatClient.prompt().messages(messages)
                .stream().chatResponse()
                .map(ChatResponseStreamConverter::toSseEvent);
        return AiElementsSse.writeTo(new SseEmitter(120_000L), events);
    }
}
```

See [`samples/ai-elements-sample`](samples/ai-elements-sample/) for a full working app,
including the mock mode that proves the wire bytes without a model API key.

## WebFlux

On a reactive stack, return `Flux<ServerSentEvent<String>>` from a `@RestController`
with `produces = "text/event-stream"` and let `AiElementsSse.streamChat(...)` do the
conversion and append the `[DONE]` sentinel:

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    @PostMapping(produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> chat(final @RequestBody ChatRequest request,
                                              final ServerHttpResponse response) {
        applyStreamHeaders(response);
        final var messages = ChatRequestConverter.toSpringAiMessages(request);
        final var responses = chatClient.prompt().messages(messages)
                .stream().chatResponse();
        return AiElementsSse.streamChat(responses);
    }
}
```

Because `ai-elements-spring-ai` exposes the servlet (SseEmitter) helpers as `api`
dependencies, a WebFlux consumer must keep the servlet classes off its runtime
classpath or Spring Boot will start in servlet mode instead of reactive mode. The
[`samples/ai-elements-webflux-sample`](samples/ai-elements-webflux-sample/) shows the
exclusions and runs on Netty.

```bash
./gradlew -p ai/ai-elements/samples/ai-elements-webflux-sample bootRun
curl -N -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"id":"chat_1","messages":[{"id":"m1","role":"user","content":"hello"}],"trigger":"submit-message","messageId":null}'
```

## What this does NOT do

Honest boundaries, so you can plan around them:

- **No provider or transport autoconfiguration yet.** There is no
  `ai-elements-spring-boot-starter` in this iteration; you wire the `ChatClient`
  (Spring AI does that) and the controller (the sample shows how). A starter that
  auto-exposes the endpoint is the planned next step.
- **Tool output is not orchestrated here.** We emit `tool-input-start`,
  `tool-output-available`, and `tool` events, but deciding which tool to call and when
  is the app's job — the underlying Spring AI tooling drives that.
- **No client SDK.** We produce bytes for the Vercel/AI-SDK-style stream; we do not
  ship the browser-side parser. The frontend (e.g. ai-elements-vue) reads the stream.
- **Servlet and reactive wiring differ.** `AiElementsSse` has both `SseEmitter` and
  `Flux` paths; pick the one that matches your stack.

You will still need to learn Spring AI (client, prompts, tools) and the shape of the
UI-message-stream protocol. This layer removes the protocol formatting boilerplate,
not the underlying knowledge.

## Who it's for

- **Beginners:** a working streaming AI chatbot backend without hand-rolling an SSE
  parser and event formatter.
- **Experienced developers:** skip the event-mapping and keep only your prompts, tools,
  and business logic.
- **Bootstrap projects:** an MVP chat endpoint in one sprint, with clean seams to add
  providers or a starter later.
