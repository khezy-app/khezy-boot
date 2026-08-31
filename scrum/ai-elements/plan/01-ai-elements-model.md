# Task 1 — AEL-01 scaffold + AEL-02 `ai-elements-model`

## Objective

Create the composite-build skeletons for the three ai-elements modules and wire them
into the root `settings.gradle` (AEL-01), then fully implement **`ai-elements-model`** —
the pure protocol records (request models, `SseEvent` stream model, common types)
with serialization tests (AEL-02). After this task, `ai-elements-model` compiles,
tests green, Checkstyle clean — with zero Spring dependencies in the module.

## Hand-off context

- **Contract**: INDEX §3 (module recipe, conventions), §4 (decisions D2, D3, D4, D9),
  §5 (naming analysis — model names here are the *result* of that analysis).
- **What NOT to re-explore**: the AI SDK wire names are pinned in INDEX §5; the
  reference `ai-harness` records are ported from
  `ai-harness/src/main/java/io/github/khezyapp/aiharness/aisdk/model/**` — do not
  re-read the docs for naming. Do not touch provider-spec types (out of scope).
- **Style skills to load**: `.opencode/skills/khezy-coding-style/SKILL.md` (and
  checkstyle gotchas if referenced by it).

## Design notes

- Package base `io.github.khezyapp.aielements.model` with sub-packages
  `request`, `response`, `common`.
- Jackson **annotations only** (D2/D9). `MessagePart` uses `@JsonTypeInfo` +
  `@JsonSubTypes` (D4) — same approach as `ai-harness` `UIPart`.
- `ToolResultPart` is **not** in the `MessagePart` union (D3).
- `SseEvent` is a sealed interface with nested records; object payloads arrive as
  pre-serialized JSON strings (D2).

## Files to create / edit

### AEL-01 scaffold

- `ai/ai-elements/ai-elements-model/settings.gradle`
  ```groovy
  pluginManagement {
      includeBuild("../../../build-logic")
  }
  rootProject.name = "ai-elements-model"
  ```
- `ai/ai-elements/ai-elements-model/build.gradle`
  ```groovy
  plugins {
      id("khezy.java-library")
  }
  group = "io.github.khezyapp"
  version = "1.0.0"
  dependencies {
      api "com.fasterxml.jackson.core:jackson-annotations"
  }
  mavenPublishing {
      pom {
          name = "Khezy AI Elements Model"
          description = """
          Pure wire-protocol records for the ai-elements (AI SDK UI message stream)
          integration: request models, typed SSE events, finish reasons and usage.
          No Spring dependencies."""
      }
  }
  ```
- `ai/ai-elements/ai-elements-spring-ai/settings.gradle` + `build.gradle` — **skeleton
  only** (module must exist so AEL-03 only adds sources). Mirror the recipe; plugin
  `khezy.java-library`, group/version as INDEX §3. No deps yet (added in task 2).
- `ai/ai-elements/samples/ai-elements-sample/settings.gradle` — **skeleton only**:
  ```groovy
  pluginManagement { includeBuild("../../../../build-logic") }
  includeBuild("../../ai-elements-model")
  includeBuild("../../ai-elements-spring-ai")
  rootProject.name = "ai-elements-sample"
  ```
- Root `settings.gradle`: append the three `includeBuild(...)` lines from INDEX §3.

### AEL-02 — `ai-elements-model` sources (`src/main/java`)

**`io.github.khezyapp.aielements.model.request.ChatRequest`**
```java
public record ChatRequest(
    String id,
    List<ChatMessage> messages,
    String trigger,      // "submit-message" | "regenerate-message" | "resume-stream"
    String messageId
) {}
```

**`io.github.khezyapp.aielements.model.request.ChatMessage`**
```java
public record ChatMessage(
    String id,
    String role,          // "user" | "assistant" | "system"
    String content,       // legacy text (may be empty)
    List<MessagePart> parts
) {}
```

**`io.github.khezyapp.aielements.model.request.MessagePart`** (sealed)
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = ReasoningPart.class, name = "reasoning"),
    @JsonSubTypes.Type(value = SourceUrlPart.class, name = "source-url"),
    @JsonSubTypes.Type(value = FilePart.class, name = "file"),
    @JsonSubTypes.Type(value = ToolInvocationPart.class, name = "tool-invocation"),
    @JsonSubTypes.Type(value = StepStartPart.class, name = "step-start"),
    @JsonSubTypes.Type(value = StepFinishPart.class, name = "step-finish")
})
public sealed interface MessagePart
    permits TextPart, ReasoningPart, SourceUrlPart, FilePart,
            ToolInvocationPart, StepStartPart, StepFinishPart {}
```

**Part records** (all `implements MessagePart`, each with a `String type` first field
matching the JSON discriminator):
```java
public record TextPart(String type, String text) implements MessagePart {}
public record ReasoningPart(String type, String text) implements MessagePart {}
public record SourceUrlPart(String type, String url, String title) implements MessagePart {}
public record FilePart(String type, String name, String mimeType, String data) implements MessagePart {}
public record ToolInvocationPart(
    String type, String toolCallId, String toolName,
    String state,                    // "call" | "result" | "partial-call"
    Map<String, Object> args,
    Object result
) implements MessagePart {}
public record StepStartPart(String type) implements MessagePart {}
public record StepFinishPart(String type) implements MessagePart {}
```

**`io.github.khezyapp.aielements.model.common.FinishReason`** (enum) — from
`ai-harness .../common/FinishReason`: `STOP("stop")`, `LENGTH("length")`,
`CONTENT_FILTER("content-filter")`, `TOOL_CALLS("tool-calls")`, `ERROR("error")`,
`OTHER("other")`; `value()`, `static FinishReason fromString(String)`.

**`io.github.khezyapp.aielements.model.common.Usage`** (record)
```java
public record Usage(
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Integer reasoningTokens,
    Integer cachedInputTokens
) {
    public static Usage empty() { return new Usage(0, 0, 0, null, null); }
}
```

**`io.github.khezyapp.aielements.model.response.SseEvent`** — the core of this task.
Sealed interface; each nested record implements a `type()` and a `json()` (minified
payload, no `data:` prefix), plus `default String toWireFormat() { return "data: " +
json() + "\n\n"; }`. Static `String done()` → `"data: [DONE]\n\n"`. Also a private
`escape(String)` helper (backslash, quote, `\n`, `\r`, `\t`; null → empty).

```java
public sealed interface SseEvent permits
    SseEvent.Start, SseEvent.StartStep, SseEvent.TextStart, SseEvent.TextDelta,
    SseEvent.TextEnd, SseEvent.ReasoningStart, SseEvent.ReasoningDelta,
    SseEvent.ReasoningEnd, SseEvent.SourceUrl, SseEvent.ToolInputStart,
    SseEvent.ToolInputAvailable, SseEvent.ToolOutputAvailable,
    SseEvent.FinishStep, SseEvent.Finish, SseEvent.ErrorEvent {

    String type();
    String json();
    default String toWireFormat() { return "data: " + json() + "\n\n"; }
    static String done() { return "data: [DONE]\n\n"; }

    record Start(String messageId) implements SseEvent {           // type "start"
        // json: {"type":"start"} or {"type":"start","messageId":"..."} when non-null
    }
    record StartStep() implements SseEvent {                       // type "start-step"
    }
    record TextStart(String id) implements SseEvent {              // type "text-start"
    }
    record TextDelta(String id, String delta) implements SseEvent {// type "text-delta"
        // delta MUST be escaped
    }
    record TextEnd(String id) implements SseEvent {                // type "text-end"
    }
    record ReasoningStart(String id) implements SseEvent {         // type "reasoning-start"
    }
    record ReasoningDelta(String id, String delta) implements SseEvent { // type "reasoning-delta"
    }
    record ReasoningEnd(String id) implements SseEvent {           // type "reasoning-end"
    }
    record SourceUrl(String sourceId, String url, String title) implements SseEvent {
        // type "source-url"; title omitted when null
    }
    record ToolInputStart(String toolCallId, String toolName) implements SseEvent {
        // type "tool-input-start"
    }
    record ToolInputAvailable(String toolCallId, String toolName, String inputJson)
        implements SseEvent {  // type "tool-input-available"; inputJson embedded raw
    }
    record ToolOutputAvailable(String toolCallId, String outputJson) implements SseEvent {
        // type "tool-output-available"; outputJson embedded raw
    }
    record FinishStep() implements SseEvent {                      // type "finish-step"
    }
    record Finish(FinishReason finishReason, Usage usage) implements SseEvent {
        // type "finish"; usage rendered inline from the Usage record (never null)
    }
    record ErrorEvent(String errorText) implements SseEvent {     // type "error"
    }
}
```

Note on `record Start(String messageId)`: the reference `SSEMessage.start(messageId)`
embeds `messageId` only when non-null — keep that. Checkstyle requires braces on all
blocks and 120-char lines; the `json()` bodies are string concatenation — keep each
line ≤ 120 chars.

**`io.github.khezyapp.aielements.model.response.SseEventBuilder`** — transport-free
accumulator (replaces `SseStreamBuilder`):
```java
public final class SseEventBuilder {
    private final List<SseEvent> events = new ArrayList<>();
    public SseEventBuilder start(String messageId) { ... }
    public SseEventBuilder startStep() { ... }
    public SseEventBuilder text(String id, String text) { events.add(textStart); delta; end; }
    public SseEventBuilder textDelta(String id, String delta) { ... }
    public SseEventBuilder reasoning(String id, String text) { ... }
    public SseEventBuilder sourceUrl(String sourceId, String url, String title) { ... }
    public SseEventBuilder toolInputStart(String toolCallId, String toolName) { ... }
    public SseEventBuilder toolInputAvailable(String toolCallId, String toolName, String inputJson) { ... }
    public SseEventBuilder toolOutputAvailable(String toolCallId, String outputJson) { ... }
    public SseEventBuilder finish(FinishReason reason, Usage usage) { finishStep + finish }
    public SseEventBuilder error(String errorText) { ... }
    public List<SseEvent> build() { return List.copyOf(events); }
    public String buildWireFormat() { /* events + [DONE], no trailing-marker guess */ }
}
```

## Tests (`src/test/java`)

Use JUnit 5 + AssertJ. Package mirror `io.github.khezyapp.aielements.model.*`.

1. `ChatRequestJsonRoundTripTest` — with a test-scope `com.fasterxml.jackson.databind.ObjectMapper`:
   - `shouldRoundTripChatRequestWithAllPartTypes` — serialize a `ChatRequest` whose
     `ChatMessage.parts` contains one of every `MessagePart` subtype; deserialize and
     assert each part's concrete type survives (`TextPart`, `ReasoningPart`,
     `SourceUrlPart`, `FilePart`, `ToolInvocationPart`, `StepStartPart`,
     `StepFinishPart`).
   - `shouldDiscriminateOnTypeField` — a part serialized with `"type":"tool-invocation"`
     deserializes to `ToolInvocationPart`.
   - `shouldNotContainToolResultPart` — assert `MessagePart` permitted types excludes
     `ToolResultPart` (protects D3).
2. `SseEventWireFormatTest` — exact-string asserts:
   - `textDeltaEscapesQuotesAndNewlines` → `data: {"type":"text-delta","id":"0","delta":"a\\\"b\\nc"}\n\n`
   - `startWithNullMessageIdOmitsField` → `data: {"type":"start"}\n\n`
   - `startWithMessageIdIncludesField`
   - `finishRendersUsageInline` → `data: {"type":"finish","finishReason":"stop","usage":{"inputTokens":1,"outputTokens":2,"totalTokens":3}}\n\n`
   - `doneReturnsSentinel` → `data: [DONE]\n\n`
   - `toolInputAvailableEmbedsInputJsonRaw` (input json `{"q":"x"}` appears unescaped)
   - `sourceUrlOmitsNullTitle`
3. `SseEventBuilderTest` — `build()` returns immutable list in insertion order;
   `buildWireFormat()` concatenates each event's `toWireFormat()` and ends with `[DONE]`.
4. `FinishReasonTest` — `fromString` maps all values + `OTHER` fallback; `value()` round-trips.

## Acceptance criteria

```bash
./gradlew :ai:ai-elements:ai-elements-model:test
./gradlew :ai:ai-elements:ai-elements-model:checkstyleMain
```
Both green. `ai-elements-model/build.gradle` has **no** Spring dependency (verify with
`./gradlew :ai:ai-elements:ai-elements-model:dependencies --configuration runtimeClasspath`
if unsure). The `ai-elements-spring-ai` and sample skeletons exist and resolve in the
composite build (`./gradlew projects` lists all three).

## Hand-off to next task

Log into `00-HANDOFF.md`:
- All public model signatures **as built** (especially the `SseEvent` nested records
  and their exact `json()` field order — AEL-03's stream converter constructs these).
- The `MessagePart` permitted-type list verbatim (AEL-03 converter switches on it).
- Whether `Start(messageId)` kept the omit-when-null behavior.
- Any checkstyle surprises (long `json()` lines) and how they were resolved.
- Confirm the composite wiring works from root before moving on.
