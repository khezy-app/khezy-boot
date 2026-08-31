# KHEZY AI Elements — Model

The wire model for the Vercel **UI-message-stream** protocol. This module owns the
request DTOs a chat client sends (`ChatRequest`, `ChatMessage`, the message `parts`)
and the outgoing event model (`SseEvent`, `SseEventBuilder`, `Usage`, `FinishReason`)
that becomes the SSE frames.

`io.github.khezyapp:ai-elements-model:1.0.0` — depends only on the shared Jackson 2.x
annotations (`com.fasterxml.jackson.annotation.*`), so the DTOs serialize identically
regardless of whether the consuming app runs Jackson 2 or Jackson 3.

## What's here

| Package | Classes | Role |
|---|---|---|
| `model.request` | `ChatRequest`, `ChatMessage`, `MessagePart` + typed parts (`TextPart`, `ReasoningPart`, `StepStartPart`, `StepFinishPart`, `FilePart`, `SourceUrlPart`, `ToolInvocationPart`) | Request that a client posts to an ai-elements endpoint |
| `model.response` | `SseEvent`, `SseEventBuilder` | The typed model behind each `data: {...}` frame |
| `model.common` | `Usage`, `FinishReason` | Shared field types used by events |

## What this does NOT do

- **No transport.** This module is pure data — it has no Spring AI and no HTTP/SSE
  wiring. Encoding to wire bytes happens in `ai-elements-spring-ai`.
- **No validation or conversation state.** It models a single request/event; chat
  history and persistence are your job.

## Who it's for

Anyone who needs the ai-elements wire model without the Spring AI conversion — for
example a non-Spring producer or a consumer-side parser.
