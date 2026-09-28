# KHEZY AI Elements — Spring AI

Adapts a Spring AI `ChatClient` / `ChatResponse` stream into the Vercel
**UI-message-stream** SSE bytes. This is the module that makes Spring AI speak the
protocol the frontend expects.

`io.github.khezyapp:ai-elements-spring-ai:1.0.0` — depends on `spring-ai-client-chat`,
`spring-webmvc` and `jakarta.servlet-api`, and on the
[`ai-elements-model`](../ai-elements-model/) wire model.

It is a standalone Gradle composite build
(`ai/ai-elements/ai-elements-spring-ai/settings.gradle`) and is wired into the root build
via `includeBuild`.

## What's here

All classes live under `io.github.khezyapp.aielements.springai`.

| Class | Role |
|---|---|
| `convert.ChatRequestConverter` | Maps a `ChatRequest` (and its message parts) into Spring AI `Message`s for the prompt. Handles `trigger` semantics (`regenerate-message` trimming), tool parts (`dynamic-tool` and provider `tool-<name>`), and file parts (data-URL or hosted URL). An assistant turn with an inline tool result expands into an `AssistantMessage` + `ToolResponseMessage`. |
| `convert.ChatResponseConverter` | Maps a single Spring AI `ChatResponse` into an ai-elements `ChatMessage` / `Usage` / `FinishReason` (non-streaming case). Tool calls become `dynamic-tool` `ToolPart`s. |
| `convert.ChatResponseStreamConverter` | Maps a streaming `Flux<ChatResponse>` into `Flux<SseEvent>` (`start`, `start-step`, `text-*`, `reasoning-*`, `tool-*`, `finish-step`, `finish`). Tool chunks are marked `dynamic:true`. |
| `convert.ChatHistoryConverter` | Maps a stored `List<Message>` back into `List<ChatMessage>` (`dynamic-tool` parts, base64 `file` parts), merging tool results into the assistant turn that invoked them. |
| `sse.AiElementsSse` | Transport facade: writes events over servlet `SseEmitter` or reactive `Flux<ServerSentEvent<String>>`, applies the required headers, and always terminates with `[DONE]`. |

## Servlet usage (Spring MVC)

Build the `ChatClient` the Spring AI way, then hand its response stream to the facade.

```java
import io.github.khezyapp.aielements.model.request.ChatRequest;
import io.github.khezyapp.aielements.springai.convert.ChatRequestConverter;
import io.github.khezyapp.aielements.springai.sse.AiElementsSse;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(final ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping(produces = "text/event-stream")
    public SseEmitter chat(final @RequestBody ChatRequest request,
                           final HttpServletResponse response) {
        AiElementsSse.applyStreamHeaders(response);
        final var messages = ChatRequestConverter.toSpringAiMessages(request);
        final var responses = chatClient.prompt().messages(messages)
                .stream().chatResponse();
        return AiElementsSse.streamToEmitter(responses, 120_000L);
    }
}
```

For finer control, convert first and write the events yourself:

```java
final Flux<SseEvent> events = ChatResponseStreamConverter.toEvents(responses, "msg_1");
return AiElementsSse.writeTo(new SseEmitter(120_000L), events);
```

Passing an explicit `messageId` to `toEvents(stream, id)` makes the `start` event id and
the text-block id match a stored id, which is what `regenerate-message` trims against.

## WebFlux usage

```java
@PostMapping(produces = "text/event-stream")
public Flux<ServerSentEvent<String>> chat(final @RequestBody ChatRequest request) {
    final var messages = ChatRequestConverter.toSpringAiMessages(request);
    final var responses = chatClient.prompt().messages(messages).stream().chatResponse();
    return AiElementsSse.streamChat(responses);
}
```

`streamChat` = `ChatResponseStreamConverter.toEvents(...)` +
`AiElementsSse.toServerSentEvents(...)`, appending `[DONE]` once. There is no reactive
overload of `applyStreamHeaders` yet, so set `Cache-Control`, `X-Accel-Buffering` and
`X-Vercel-AI-UI-Message-Stream: v1` on the `ServerHttpResponse` in the reactive path.

## Use cases

### Regenerate a message

The UI replays history with `trigger = "regenerate-message"` and the id to redo;
`ChatRequestConverter` drops that message and everything after it before the model call:

```java
final var messages = ChatRequestConverter.toSpringAiMessages(request); // trimmed
// or inspect the boundary only:
final var visible = ChatRequestConverter.filterForTrigger(request);
```

### Load history into the UI

```java
import io.github.khezyapp.aielements.springai.convert.ChatHistoryConverter;

final List<Message> stored = chatMemory.get(conversationId);
final List<ChatMessage> uiMessages = ChatHistoryConverter.toChatMessages(stored);
```

`ChatHistoryConverter` also has `toChatMessage(Message)` for a single message. It sources
each `ChatMessage` id from Spring AI metadata (`id`, then `messageId`) and falls back to a
UUID — persist those ids if regeneration must work.

### Map metadata directly

```java
final Usage usage = ChatResponseConverter.toUsage(springAiResponse.getMetadata().getUsage());
final FinishReason reason = ChatResponseConverter.toFinishReason("stop");
final ChatMessage message = ChatResponseConverter.toChatMessage(springAiResponse, "msg_1");
```

## What this does NOT do

- **No provider or transport autoconfiguration.** You build the `ChatClient` with Spring
  AI and wire the controller yourself. A starter that auto-exposes the endpoint is a
  possible next step, not part of this module.
- **No tool orchestration.** It emits `tool-*` events from the response stream, but
  choosing when to call tools is the app's job via Spring AI tooling.
- **Not every chunk is emitted.** The model defines the full AI SDK chunk set
  (`tool-input-delta`, `tool-input-error`, `tool-output-error`, `tool-output-denied`,
  `tool-approval-*`, `source-document`, `file`, `reasoning-file`, `message-metadata`,
  `reset-step`, `custom`, `data-*`, `abort`), but the converters only emit what Spring AI
  exposes — text, reasoning, tool input/output, sources and usage. Approval, deltas,
  documents and data parts are model-ready for callers/`SseEventBuilder`, not produced
  from a `ChatResponse`.
- **No browser/client SDK** — it only produces bytes.
- **No memory/persistence.** History is fetched and stored by your app; this module only
  converts shapes.

## Who it's for

A Spring Boot app that wants a `POST /api/chat` (or any endpoint) returning an
ai-elements `text/event-stream`, without hand-rolling the event conversion or the `[DONE]`
lifecycle. See the [sample app](../samples/ai-elements-sample/) for a full working
controller.

## Testing

```sh
./gradlew :ai-elements-spring-ai:test
```

Converter tests mock `ChatResponse` streams and assert the emitted `SseEvent`
sequence; `AiElementsSseTest` verifies wire framing and the single `[DONE]` sentinel for
both transports.
