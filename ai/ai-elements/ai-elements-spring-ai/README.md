# KHEZY AI Elements — Spring AI

Adapts a Spring AI `ChatClient`/`ChatResponse` stream into the Vercel
**UI-message-stream** SSE bytes. This is the module that makes Spring AI speak the
protocol the frontend expects.

`io.github.khezyapp:ai-elements-spring-ai:1.0.0` — depends on `spring-ai-client-chat`
and `spring-webmvc`; on the `ai-elements-model` wire model.

## What's here

| Class | Role |
|---|---|
| `convert.ChatRequestConverter` | Maps a `ChatRequest` (and its message parts) into Spring AI `Message`s for the prompt |
| `convert.ChatResponseConverter` | Maps a single Spring AI `ChatResponse` into the terminal `finish` event |
| `convert.ChatResponseStreamConverter` | Maps a spring AI streaming `ChatResponse` into the event stream (`start`, `start-step`, `text-*`, `reasoning-*`, `tool-*`, `finish-step`, `finish`) |
| `sse.AiElementsSse` | Writes events over servlet `SseEmitter` or reactive `Flux`, applies the SSE headers, and always terminates with the `[DONE]` sentinel |

## What this does NOT do

- **No provider or transport autoconfiguration** (deferred to a future starter). You
  build the `ChatClient` with Spring AI and call the converters yourself.
- **No tool orchestration.** It emits `tool-*` events from the response stream, but
  choosing when to call tools is the app's job via Spring AI tooling.
- **No browser/client SDK** — it only produces bytes.

## Who it's for

A Spring Boot app that wants a `POST /api/chat` (or any endpoint) returning an
ai-elements `text/event-stream`, without hand-rolling the event conversion or the
SSE `[DONE]` lifecycle. See the [sample app](../samples/ai-elements-sample/) for a full
working controller.
