# Task 2 — AEL-03 `ai-elements-spring-ai`: converters + stream converter

## Objective

Implement **`ai-elements-spring-ai`** — the "port" module the user asked for. It
converts **Spring AI `Message` / `ChatResponse` / `Flux<ChatResponse>` → ai-elements
model** (`ChatMessage`, parts, `SseEvent`), handles `trigger`-based conversation
trimming, maps usage + finish reason, and provides the shared `AiElementsSse` facade
(servlet + WebFlux helpers). Tests cover both conversion directions and the stream
converter's event sequence. The WebFlux/servlet transport helpers are part of this
task per INDEX decision D1.

## Hand-off context

- **Contract**: INDEX §3 (module conventions), §4 (D1, D6, D7, D8), §5 (model naming —
  the model types are as-built from task 1's handoff). Spring AI 2.0 API shapes below
  were verified via `javap` against `spring-ai-model` / `spring-ai-commons` 2.0.0.
- **What NOT to re-explore**: model sources (task 1 handoff has signatures); the AI
  SDK docs. Do NOT read the Spring AI source — the verified signatures are below.
- **Verified Spring AI 2.0 signatures** (do not guess beyond these):
  - `org.springframework.ai.chat.messages.Message` — `MessageType getMessageType()`;
    extends `Content` (`String getText()`, `Map<String,Object> getMetadata()`) and
    `MediaContent` (`List<Media> getMedia()`).
  - `MessageType` enum: `USER`, `ASSISTANT`, `SYSTEM`, `TOOL`; `getValue()`.
  - `UserMessage(String)` / `SystemMessage(String)` / `AssistantMessage(String)` ctors.
  - `AssistantMessage.getToolCalls()` → `List<AssistantMessage.ToolCall>`; `ToolCall`
    is a record `(String id, String type, String name, String arguments)`.
  - `ToolResponseMessage.getResponses()` → `List<ToolResponseMessage.ToolResponse>`;
    `ToolResponse` record `(String id, String name, String responseData)`.
  - `ChatResponse.getResult()` → `Generation`; `Generation.getOutput()` →
    `AssistantMessage`; `Generation.getMetadata()` → `ChatGenerationMetadata`
    (`.getFinishReason()` → `String`, may be null/empty).
  - `ChatResponse.getMetadata()` → `ChatResponseMetadata` (`.getUsage()` →
    `org.springframework.ai.chat.metadata.Usage`).
  - `org.springframework.ai.chat.metadata.Usage`: `Integer getPromptTokens()`,
    `Integer getCompletionTokens()`, `Integer getTotalTokens()`.
  - `ChatClient ... .stream().chatResponse()` → `Flux<ChatResponse>`;
    `.stream().content()` → `Flux<String>`.
  - `org.springframework.ai.chat.messages.MessageUtils` exists (may help reorder
    tool messages); optional.

## Design notes

- Public API is **static factory / converter classes** (no beans) — matches the
  a2a-spring-ai "plain factory methods" precedent. No auto-configuration in this task.
- Converters are **pure functions**; JSON work uses `tools.jackson.databind.ObjectMapper`
  (Jackson 3, Spring Boot 4 alignment). Accept an `ObjectMapper` or use a shared
  default — decide in-task and log it (do NOT use `com.fasterxml.jackson.databind`).
- `ToolResponseMessage` handling: a `ChatMessage` part
  `ToolInvocationPart(state="result")` maps **to** a `ToolResponseMessage`; on the way
  **back** (assistant tool calls → `ToolInvocationPart(state="call")`).
- Finish-reason mapping uses the model's `FinishReason.fromString` (task 1).

## Files to create

### `ai/ai-elements/ai-elements-spring-ai/settings.gradle`
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "ai-elements-spring-ai"
```

### `ai/ai-elements/ai-elements-spring-ai/build.gradle`
```groovy
plugins {
    id("khezy.java-library")
    id("khezy.java-use-mockito")
}
group = "io.github.khezyapp"
version = "1.0.0"
ext {
    springAiVersion = '2.0.1'
    aiElementsModelVersion = '1.0.0'
}
dependencies {
    api "io.github.khezyapp:ai-elements-model:${aiElementsModelVersion}"
    api "org.springframework.ai:spring-ai-client-chat:${springAiVersion}"
    api "org.springframework:spring-webmvc"   // SseEmitter; also pulls spring-web (ServerSentEvent)
    testImplementation "org.springframework.boot:spring-boot-starter-webmvc-test"
    testImplementation "io.projectreactor:reactor-test"
}
mavenPublishing {
    pom {
        name = "Khezy AI Elements Spring AI"
        description = """
        Ports Spring AI to the ai-elements (AI SDK UI message stream) protocol:
        converts Spring AI messages and streams into ai-elements models and typed
        SSE events, with writer helpers for both servlet (SseEmitter) and WebFlux."""
    }
}
```
(If `reactor-test` version is not resolvable via the BOM, pin it in-task and log.)

### `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatRequestConverter.java`
```java
public final class ChatRequestConverter {
    /** Maps a ChatRequest to a Spring AI message list, honoring trigger semantics. */
    public static List<Message> toSpringAiMessages(ChatRequest request);

    /** Trims the request per trigger: drops the messageId and everything after it
     *  when trigger == "regenerate-message"; otherwise returns messages unchanged. */
    public static List<ChatMessage> filterForTrigger(ChatRequest request);

    /** Converts a single ChatMessage to a Spring AI Message (role dispatch). */
    public static Message toSpringAiMessage(ChatMessage message);
}
```
Role dispatch (public behavior to lock in tests):
- `"system"` → `new SystemMessage(text)` (text = joined `TextPart`s, else `content`).
- `"user"` → `new UserMessage(text)`; if a `FilePart` exists, attach media via
  `UserMessage.builder()...media(...)` (mimeType + base64-decoded bytes → `Media`);
  if building media proves awkward, log the limitation and emit text-only in v1.
- `"assistant"` → `new AssistantMessage(text)`; for each
  `ToolInvocationPart(state="call")` map to `AssistantMessage.ToolCall(id, "function",
  toolName, argsJson)` via the shared ObjectMapper (args `Map<String,Object>` →
  JSON string).
- `"tool"` / `ToolInvocationPart(state="result")` → `ToolResponseMessage.builder()`
  with `ToolResponse(toolCallId, toolName, resultJson)`.

### `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatResponseConverter.java`
```java
public final class ChatResponseConverter {
    /** Non-streaming: ChatResponse -> a server-side ChatMessage the UI can render. */
    public static ChatMessage toChatMessage(ChatResponse response, String id);
    /** Spring AI Usage -> ai-elements Usage (promptTokens->inputTokens, etc.). */
    public static Usage toUsage(org.springframework.ai.chat.metadata.Usage usage);
    /** Spring AI finish-reason string -> FinishReason (fromString, null-safe). */
    public static FinishReason toFinishReason(String springAiReason);
    /** AssistantMessage text + tool calls -> List<MessagePart> (TextPart + ToolInvocationPart). */
    public static List<MessagePart> toParts(AssistantMessage message);
}
```
`toChatMessage`: parts from `toParts`; tool calls become
`ToolInvocationPart("tool-invocation", id, name, "call", parsedArgs, null)` with the
record's `arguments` JSON string parsed via the shared ObjectMapper.

### `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatResponseStreamConverter.java`
```java
public final class ChatResponseStreamConverter {
    /** Flux<ChatResponse> -> Flux<SseEvent> (start, start-step, text block "0", finish, finish-step). */
    public static Flux<SseEvent> toEvents(Flux<ChatResponse> stream);
    /** Overload: caller supplies the starting text-block id (for D6 multi-block). */
    public static Flux<SseEvent> toEvents(Flux<ChatResponse> stream, String textBlockId);
}
```
Emission rules (lock in tests):
1. Prefix `Start(null)` + `StartStep()` exactly once (use `concatWith`/state flag).
2. Per `ChatResponse` `Generation`: if `output.getText()` non-blank → emit
   `TextStart(id)` once then `TextDelta(id, text)` per response. If reasoning text is
   present in `ChatGenerationMetadata`/metadata (D8), emit `ReasoningStart/Deltas/End`
   with the next id; otherwise none.
3. If `output.getToolCalls()` non-empty → `ToolInputStart(toolCallId, toolName)` then
   `ToolInputAvailable(toolCallId, toolName, argsJson)` (D7: **no** auto
   `tool-output-available`).
4. On `ChatGenerationMetadata.getFinishReason()` non-null → close open blocks
   (`TextEnd(id)`), then `FinishStep()` + `Finish(reason, usage)` (usage from
   `ChatResponseMetadata.getUsage()` via `toUsage`; `Usage.empty()` if null).
5. Errors: append `ErrorEvent(e.getMessage())` before the terminal finish — handled by
   the writer (task 2 covers `AiElementsSse`, see below), converter stays pure.
6. The `[DONE]` sentinel is **not** emitted here — the transport writer appends it
   (single place, avoids double sentinels).

Implementation hint: keep a small mutable state object (open-text-block flag, emitted
start flag, current id) used inside `map`/`concatMap` over the Flux. If using a
`AtomicBoolean`/holder is simpler than operators, that is fine — document the choice.

### `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/sse/AiElementsSse.java`
The shared SSE facade (both transports, per D1):

```java
public final class AiElementsSse {
    // ---- wire-format string helpers (shared) ----
    public static String toWireFormat(List<SseEvent> events);          // events + [DONE]
    public static List<String> toWireLines(List<SseEvent> events);     // without [DONE]

    // ---- servlet (SseEmitter) ----
    public static SseEmitter writeTo(SseEmitter emitter, List<SseEvent> events);
    public static SseEmitter writeTo(SseEmitter emitter, Flux<SseEvent> events);
    // convenience: build an emitter for a Flux<ChatResponse> in one call
    public static SseEmitter streamToEmitter(Flux<ChatResponse> responses, long timeout);

    // ---- WebFlux (Flux<ServerSentEvent<String>>) ----
    public static Flux<ServerSentEvent<String>> toServerSentEvents(List<SseEvent> events);
    public static Flux<ServerSentEvent<String>> toServerSentEvents(Flux<SseEvent> events);
    // convenience: full pipeline Flux<ChatResponse> -> typed UI-stream SSE Flux
    public static Flux<ServerSentEvent<String>> streamChat(Flux<ChatResponse> responses);

    // ---- required headers (both transports) ----
    public static void applyStreamHeaders(HttpServletResponse response); // Cache-Control no-cache,
        // X-Accel-Buffering no, X-Vercel-AI-UI-Message-Stream v1
}
```

Servlet semantics: `writeTo(emitter, events)` sends each event via
`SseEmitter.event().data(json)` then `complete()`; the `Flux` overload subscribes,
sends on each `next`, `complete()` on completion, `completeWithError(e)` on error, and
appends `[DONE]`. WebFlux: each `SseEvent` → `ServerSentEvent.builder(json).build()`,
then a terminal `ServerSentEvent.builder("[DONE]").build()`.

## Tests

1. `ChatRequestConverterTest`:
   - `regenerateTriggerTrimsFromMessageId` — request with `trigger=regenerate-message`,
     `messageId=m2` over `[m1,m2,m3]` → spring-ai messages built from `[m1]` only.
   - `submitTriggerKeepsAllMessages`.
   - `systemUserAssistantRolesMapToMessageTypes`.
   - `assistantToolInvocationMapsToToolCall` — `ToolCall` has id/name/parsed args.
   - `toolResultPartMapsToToolResponseMessage`.
2. `ChatResponseConverterTest`:
   - `mapsUsageFields` — promptTokens/completionTokens/totalTokens → input/output/total.
   - `mapsFinishReasonViaFromString` incl. unknown → `OTHER`, null → `OTHER`.
   - `assistantTextAndToolCallsBecomeParts` — `TextPart` + `ToolInvocationPart` with
     parsed `args` map and state `"call"`.
3. `ChatResponseStreamConverterTest` (Mockito for `ChatResponse`/`Generation`:
   build real `AssistantMessage` instances with a Mockito `ChatResponse`/`Generation`
   or real constructors where available):
   - `emitsStartThenStartStepThenDeltasThenFinish` — two text deltas → exact
     `SseEvent` sequence `[Start(null), StartStep(), TextStart("0"), TextDelta("0", a),
     TextDelta("0", b), TextEnd("0"), FinishStep(), Finish(stop, usage)]`; no `[DONE]`.
   - `emitsToolInputStartAndAvailableForToolCall` (no output event — protects D7).
   - `prefixesStartExactlyOnce` for a 3-response stream.
   - `finishUsesEmptyUsageWhenMetadataNull`.
4. `AiElementsSseTest`:
   - `toServerSentEventsAddsDoneSentinel` — `StepVerifier` asserts last element is
     `[DONE]` and earlier elements carry the event `json`.
   - `streamChatPipelinesChatResponsesIntoUiEvents` — feed a mocked `Flux<ChatResponse>`,
     assert the `ServerSentEvent<String>` data strings match `text-delta` etc.
   - servlet: `writeToEmitterCompletesAfterEvents` — use a real `SseEmitter` with a
     `SseEmitter.SseEventBuilder` capture, or an `org.springframework.mock.web`
     `MockHttpServletResponse` + `StandardServletAsyncWebRequest`; if that proves
     brittle, assert via the wire-lines helper + emitter completion callback.

## Acceptance criteria

```bash
./gradlew :ai:ai-elements:ai-elements-spring-ai:test
./gradlew :ai:ai-elements:ai-elements-spring-ai:checkstyleMain
./gradlew :ai:ai-elements:ai-elements-spring-ai:checkstyleTest
```
Green. The module compiles against `ai-elements-model` via the composite build. Public
surface = the 3 converter classes + `AiElementsSse` only (no beans, no auto-config).

## Hand-off to next task

Log into `00-HANDOFF.md`:
- Exact signatures of `ChatRequestConverter`, `ChatResponseConverter`,
  `ChatResponseStreamConverter`, `AiElementsSse` **as built**.
- Which ObjectMapper (Jackson 3 `tools.jackson`) decision was made and how it's shared.
- Any deviation from emission rules (e.g. text block id handling, reasoning support)
  and any Spring AI builder surprises (`UserMessage.builder()` media, `ToolResponseMessage.builder()`).
- Confirmed servlet `SseEmitter` test strategy (which approach worked).
- A sample curl-able stream is NOT yet available — the sample task (AEL-04) wires it.
