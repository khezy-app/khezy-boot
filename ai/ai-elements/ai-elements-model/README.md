# KHEZY AI Elements — Model

The wire model for the Vercel **UI-message-stream** protocol. This module owns the
request DTOs a chat client posts (`ChatRequest`, `ChatMessage`, the message `parts`)
and the outgoing event model (`SseEvent`, `SseEventBuilder`, `Usage`, `FinishReason`)
that becomes the SSE frames.

`io.github.khezyapp:ai-elements-model:1.0.0` — depends only on the shared Jackson 2.x
annotations (`com.fasterxml.jackson.annotation.*`), so the DTOs serialize identically
whether the consuming app runs Jackson 2 or Jackson 3.

It is a standalone Gradle composite build
(`ai/ai-elements/ai-elements-model/settings.gradle`) and is wired into the root build via
`includeBuild`.

## What's here

| Package | Classes | Role |
|---|---|---|
| `io.github.khezyapp.aielements.model.request` | `ChatRequest`, `ChatMessage`, `MessagePart` + typed parts (`TextPart`, `ReasoningPart`, `SourceUrlPart`, `SourceDocumentPart`, `FilePart`, `ReasoningFilePart`, `StepStartPart`, `ToolPart`, `CustomPart`, `UnknownPart`) | The request a client posts to an ai-elements endpoint |
| `io.github.khezyapp.aielements.model.response` | `SseEvent` (sealed), `SseEventBuilder` | The typed model behind each `data: {...}` frame |
| `io.github.khezyapp.aielements.model.common` | `Usage`, `FinishReason` | Shared field types used by events |

## Request shape

```java
public record ChatRequest(String id, List<ChatMessage> messages,
                          String trigger, String messageId, String model) {

    // Convenience constructor for the pre-model request shape.
    public ChatRequest(String id, List<ChatMessage> messages,
                       String trigger, String messageId) {
        this(id, messages, trigger, messageId, null);
    }
}

public record ChatMessage(String id, String role, String content,
                          List<MessagePart> parts) {}
```

- `trigger` is one of `"submit-message"`, `"regenerate-message"`, `"resume-stream"`.
- `role` is `"user"`, `"assistant"`, `"system"` or `"tool"` (the converters map
  `"tool"` to a Spring AI `ToolResponseMessage`).
- `model` is an optional client-selected model id (the backend falls back to its own
  configured model when absent).
- `parts` is the structured form; `content` is the legacy plain-text form (may be empty).
- `@JsonIgnoreProperties(ignoreUnknown = true)` on both records, so extra AI SDK fields
  do not 400 the request.

### Message parts

`MessagePart` is a sealed union implementing the AI SDK UI-message part union. The JSON
`type` field is the discriminator and is also bound to each part's own `type` component
(`@JsonTypeId`), so it is written exactly once. Tool results are **not** a separate part
type — a `ToolPart` carries its `input` and `output` inline.

| Wire `type` | Record | Key fields |
|---|---|---|
| `text` | `TextPart` | `text`, `state`, `providerMetadata` |
| `reasoning` | `ReasoningPart` | `id`, `text`, `state`, `providerMetadata` |
| `source-url` | `SourceUrlPart` | `sourceId`, `url`, `title`, `providerMetadata` |
| `source-document` | `SourceDocumentPart` | `sourceId`, `mediaType`, `title`, `filename`, `providerMetadata` |
| `file` | `FilePart` | `mediaType`, `filename`, `url`, `providerMetadata` |
| `reasoning-file` | `ReasoningFilePart` | `mediaType`, `url`, `providerMetadata` |
| `step-start` | `StepStartPart` | — |
| `dynamic-tool` | `ToolPart` | `toolName`, `toolCallId`, `state`, `input`, `output`, `errorText`, `providerExecuted`, `title`, `approval`, `toolMetadata` |
| `custom` | `CustomPart` | `kind`, `providerMetadata` |
| `tool-<name>`, `data-<name>`, … | `UnknownPart` | every raw field, preserved verbatim |

`FilePart.url` is either a hosted URL or a data URL
(`data:<mediaType>;base64,<payload>`). `UnknownPart` is the Jackson `defaultImpl`; it
round-trips unknown `tool-<name>` and `data-<name>` parts untouched so the
`ai-elements-spring-ai` converters can still read the `tool-*` ones.

`ToolPart.state` is one of `"input-streaming"`, `"input-available"`,
`"approval-requested"`, `"approval-responded"`, `"output-available"`, `"output-error"`,
`"output-denied"`. Build the common cases with the static factories instead of the full
constructor:

```java
import io.github.khezyapp.aielements.model.request.ToolPart;

ToolPart.call("call-1", "getWeather", Map.of("city", "Siem Reap"));   // input-available
ToolPart.result("call-1", "getWeather", Map.of("city", "Siem Reap"),
                Map.of("temp", 33));                                    // output-available
toolPart.hasResult();                                                   // true once output/error is set
```

Example request body:

```json
{
  "id": "req-1",
  "trigger": "submit-message",
  "messageId": null,
  "model": "deepseek-chat",
  "messages": [
    {
      "id": "m-1",
      "role": "assistant",
      "content": "",
      "parts": [
        {"type": "text", "text": "hello"},
        {"type": "dynamic-tool", "toolName": "getWeather", "toolCallId": "call-1",
         "state": "input-available", "input": {"city": "Siem Reap"}}
      ]
    }
  ]
}
```

## Event shape

`SseEvent` is a sealed interface; each event renders a `type()` and a minified JSON
`json()` payload. `toWireFormat()` wraps it as `data: ...\n\n`, and `SseEvent.done()`
produces `data: [DONE]\n\n`.

Supported events: `start`, `start-step`, `text-start` / `text-delta` / `text-end`,
`reasoning-start` / `reasoning-delta` / `reasoning-end`, `source-url`,
`source-document`, `file`, `reasoning-file`, `tool-input-start` / `tool-input-delta` /
`tool-input-available` / `tool-input-error`, `tool-approval-request` /
`tool-approval-response`, `tool-output-available` / `tool-output-error` /
`tool-output-denied`, `finish-step`, `finish`, `message-metadata`, `reset-step`,
`custom`, `data-*`, `abort`, `error`.

Wire shape follows the AI SDK UI-message-stream: `tool-input-available` carries the
tool arguments under `input` (raw JSON, embedded unescaped), tool chunks are marked
`"dynamic":true` so the client builds `dynamic-tool` parts, and `finish` reports token
usage the standard way, nested under `messageMetadata`:

```json
{"type":"tool-input-available","toolCallId":"call_1","toolName":"getWeather","input":{"city":"Hanoi"},"dynamic":true}
{"type":"finish","finishReason":"stop","messageMetadata":{"usage":{"inputTokens":12,"outputTokens":5,"totalTokens":17}}}
```

- `FinishReason` values: `stop`, `length`, `content-filter`, `tool-calls`, `error`,
  `other` (`fromString` falls back to `other`).
- `Usage(inputTokens, outputTokens, totalTokens, reasoningTokens, cachedInputTokens)` —
  reasoning and cached-input counts may be `null`; `Usage.empty()` is all zeros.
- `finish` omits `usage` entirely when none was reported, so a client can tell
  "not reported" apart from a real zero.

## How to use it

### Read an inbound request

Deserialize the client body straight into the records:

```java
import io.github.khezyapp.aielements.model.request.ChatRequest;

final ChatRequest request = objectMapper.readValue(body, ChatRequest.class);
// request.messages() -> typed ChatMessage list, parts already polymorphic
```

### Build an event stream without Spring AI

`SseEventBuilder` is a transport-free accumulator — useful for mock mode, tests, or a
non-Spring producer (e.g. an LLM proxy, a cached replay):

```java
import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import io.github.khezyapp.aielements.model.response.SseEventBuilder;

final String wire = new SseEventBuilder()
        .start("msg_1")
        .startStep()
        .text("msg_1", "Hello ")
        .textDelta("msg_1", "world")
        .reasoning("r1", "thinking...")
        .toolInputStart("call_1", "getWeather")
        .toolInputAvailable("call_1", "getWeather", "{\"city\":\"Hanoi\"}")
        .toolOutputAvailable("call_1", "{\"temp\":31}")
        .finish(FinishReason.STOP, new Usage(12, 5, 17, null, null))
        .buildWireFormat();   // full stream ending in data: [DONE]\n\n
```

Use `build()` instead when you want the `List<SseEvent>` to hand to a transport, and
`SseEventBuilder.error("...")` to emit an `error` event. The builder also covers the
chunks the Spring AI converters do not produce: `sourceUrl`, `sourceDocument`, `file`,
`reasoningFile`, `toolInputDelta`, `toolInputError`, `toolOutputError`,
`toolOutputDenied`, `toolApprovalRequest`, `toolApprovalResponse`, `metadata`,
`resetStep`, `custom`, `data` and `abort`.

## What this does NOT do

- **No transport.** Pure data — no Spring AI, no HTTP/SSE wiring. Encoding to wire
  bytes for a live model happens in
  [`ai-elements-spring-ai`](../ai-elements-spring-ai/).
- **No validation or conversation state.** It models a single request/event; chat
  history and persistence are your job.
- **No JSON parsing of `parts` beyond annotations.** You supply a Jackson 2 or Jackson 3
  `ObjectMapper` configured by your app.

## Who it's for

Anyone who needs the ai-elements wire model without the Spring AI conversion — a
non-Spring producer, a consumer-side parser, or tests asserting exact wire bytes. It is
also the dependency base for `ai-elements-spring-ai`.

## Testing

```sh
./gradlew :ai-elements-model:test
```

`ChatRequestJsonRoundTripTest` verifies the polymorphic part round-trip, unknown-part
preservation and extra-field tolerance; `SseEventWireFormatTest` and
`SseEventBuilderTest` assert exact JSON/wire framing.
