# Bug 001 — Thinking models stream reasoning but never emit answer text

| Field | Value |
|---|---|
| Status | Fixed — `ReasoningContent.isContentFlagged` + `hasNewReasoning` delta gate in `ChatResponseStreamConverter`; verified by tests |
| Severity | High (silent data loss, no error surfaced) |
| Component | `ai-elements-spring-ai` → `ChatResponseStreamConverter`, `ReasoningContent` |
| Modules | `ai/ai-elements/ai-elements-spring-ai` |
| Source report | `/Users/touchyongan/Documents/learning/spring/mcp/.bug/001-ollama-reasoning-drops-text.md` |
| Discovered via | `mcp-client` pointed at a real thinking model (`gemma4:e2b` via Ollama OpenAI-compat) |
| Frontend impact | ai-elements UI renders the reasoning/"thinking" block, then finishes with **no assistant text** |

---

## 1. Symptom

A response that carries reasoning and then an answer emits only `reasoning-*` frames. The turn
closes cleanly (`finish-step`, `finish`, `[DONE]`) with **zero** `text-start` / `text-delta` /
`text-end`. Nothing logs an error, so it looks like "the model didn't answer".

Frame census from the consumer (`/api/chat`):

```
1 start   1 start-step   1 reasoning-start
180 reasoning-delta    1 reasoning-end
1 finish-step   1 finish   [DONE]
```

## 2. Root cause (verified)

`ChatResponseStreamConverter.StreamState.eventsFor()` — `ChatResponseStreamConverter.java:71-126`:

```java
final var reasoning = ReasoningContent.extract(output);
final var hasReasoning = Objects.nonNull(reasoning) && !reasoning.isBlank();   // :77
final var text = output.getText();
final var hasText = !hasReasoning && Objects.nonNull(text) && !text.isBlank();  // :79  <-- BUG
```

`hasText` is gated on the **absence** of reasoning. When a chunk carries both fields, the text is
dropped.

This is hit because Spring AI's streaming path attaches **cumulative** reasoning metadata to every
later chunk — including the chunks that carry the answer:

- `OpenAiChatModel` reads Ollama's non-standard `delta.reasoning` (fallback `reasoning_content`)
  in `getReasoningContent()` (`spring-ai-openai`, `OpenAiChatModel.java:1142-1154`).
- It accumulates per response id with `String::concat` (`:339-340`) and writes the accumulated
  value under metadata key **`reasoningContent`** (`:134`) on **every** generation (`:348`).

So `ReasoningContent.extract()` returns a non-blank string on the content chunks
(`ReasoningContent.java:49`, the `reasoningContent` branch), `hasReasoning` stays `true`, and the
answer is discarded every time.

> This resolves the "one open point" in the source report (§7): the key is **`reasoningContent`**,
> the value is **cumulative**, and it **persists** on content chunks. No runtime probe is needed.

### Why the existing mock/tests miss it

The unit tests and `MockChatClient` use either no reasoning metadata, or the *content-flagged*
reasoning path (`properties(Map.of("thinking", true))`) where the thinking text **is** `getText()`.
The failing shape — string `reasoningContent` metadata present **and** a distinct `text` — has no
test.

## 3. The guard exists for a reason — fix must be explicit

`ReasoningContent.extract()` has a content-flagged path (`ReasoningContent.java:45`):

```java
if (Boolean.TRUE.equals(metadata.get(IS_THOUGHT)) || Boolean.TRUE.equals(metadata.get(THINKING))) {
    return text(message);   // the thinking text IS getText()
}
```

For Anthropic/Gemini the thinking text is the message content, so emitting both channels would
duplicate the thinking. The gate must not be removed outright; the fix must make the **origin**
explicit.

## 4. Fix

### 4.1 `ReasoningContent` — expose whether reasoning came from the content flag

Add one method (do **not** change `extract`'s signature — it is used by
`ChatHistoryConverter.java:130` and by `ReasoningContentTest`):

```java
/**
 * True when the reasoning is carried as the message content itself (Anthropic/Gemini
 * content-flagged reasoning), i.e. {@code getText()} must not also be emitted as a text block.
 */
public static boolean isContentFlagged(final AssistantMessage message) {
    if (Objects.isNull(message)) {
        return false;
    }
    final var metadata = message.getMetadata();
    return Objects.nonNull(metadata)
            && (Boolean.TRUE.equals(metadata.get(IS_THOUGHT))
                    || Boolean.TRUE.equals(metadata.get(THINKING)));
}
```

`IS_THOUGHT` / `THINKING` are the existing private constants (`ReasoningContent.java:22-23`).

### 4.2 `ChatResponseStreamConverter.StreamState.eventsFor()` — replace the reasoning block

Replace `ChatResponseStreamConverter.java:73-104` (from `final var generation` through the
`if (hasText) {...}` block) with the following. Everything from the tool-call loop (`:106`) and the
`hasFinish` block (`:111`) stays unchanged; `reasoningDelta()` (`:132-140`) is unchanged.

```java
final var generation = response.getResult();
final var output = generation.getOutput();

final var reasoning = ReasoningContent.extract(output);
final var hasReasoningValue = Objects.nonNull(reasoning) && !reasoning.isBlank();
// Content-flagged reasoning (Anthropic/Gemini) *is* getText(); string-metadata reasoning
// (Ollama/OpenAI `reasoningContent`) is a separate channel, so text must still be emitted.
final var text = output.getText();
final var hasText = Objects.nonNull(text) && !text.isBlank()
        && !(hasReasoningValue && ReasoningContent.isContentFlagged(output));
final var hasToolCalls = !output.getToolCalls().isEmpty();
final var finishReason = generation.getMetadata().getFinishReason();
final var hasFinish = Objects.nonNull(finishReason) && !finishReason.isEmpty();

// Reasoning metadata is cumulative and persists on later chunks: only a growing value is
// *new* reasoning. An unchanged value is stale and must not re-open the block.
final var delta = hasReasoningValue ? reasoningDelta(reasoning) : "";
final var hasNewReasoning = !delta.isEmpty();

if (hasNewReasoning) {
    if (!reasoningOpen) {
        events.add(new SseEvent.ReasoningStart(reasoningId));
        reasoningOpen = true;
    }
    events.add(new SseEvent.ReasoningDelta(reasoningId, delta));
}
// Not `else`: text/tool/finish may arrive on the same chunk that ends the now-stale reasoning.
if (reasoningOpen && !hasNewReasoning && (hasText || hasToolCalls || hasFinish)) {
    events.add(new SseEvent.ReasoningEnd(reasoningId));
    reasoningOpen = false;
}

if (hasText) {
    if (!textBlockOpen) {
        events.add(new SseEvent.TextStart(id));
        textBlockOpen = true;
    }
    events.add(new SseEvent.TextDelta(id, text));
}
```

### 4.3 Why the delta gate is required (do not skip it)

`reasoningDelta()` is stateful and must be called exactly once per chunk. Without the
`hasNewReasoning` gate:

- If you keep the old `else if` and just drop `!hasReasoning`, then because the metadata is
  cumulative, `ReasoningStart` **re-fires on every content chunk** (the block was already closed).
- If you keep `!hasReasoning` in the close condition, `hasReasoning` is always `true` on content
  chunks, so reasoning never closes until `finish` (text is emitted, but the reasoning block wraps
  the answer; not the intended sequence).

The delta gate produces: reasoning-only chunks stream deltas; on the first chunk with real content
(reasoning unchanged) the block closes, then text starts.

## 5. Required tests

Add to `ChatResponseStreamConverterTest` (keep all existing tests — `emitsReasoningBeforeText` and
`emitsOnlyNewSuffixForCumulativeReasoning` must still pass):

```java
@Test
@DisplayName("emits text when cumulative reasoning metadata persists onto a content chunk")
void emitsTextWhenReasoningMetadataPersists() {
    // Ollama shape: cumulative `reasoningContent` metadata stays attached to the answer chunk.
    final var r1 = new ChatResponse(List.of(new Generation(reasoningMeta("Thinking"))));
    final var r2 = new ChatResponse(List.of(new Generation(reasoningMeta("Thinking Process"))));
    final var r3 = new ChatResponse(List.of(new Generation(
            AssistantMessage.builder()
                    .content("Hello")
                    .properties(Map.of("reasoningContent", "Thinking Process"))
                    .build(),
            ChatGenerationMetadata.builder().finishReason("stop").build())));

    final var events = ChatResponseStreamConverter.toEvents(Flux.just(r1, r2, r3))
            .collectList()
            .block();
    assertNotNull(events);

    final var start = (SseEvent.Start) events.get(0);
    final var reasoningId = "reasoning-" + start.messageId();
    assertEquals(new SseEvent.StartStep(), events.get(1));
    assertEquals(new SseEvent.ReasoningStart(reasoningId), events.get(2));
    assertEquals(new SseEvent.ReasoningDelta(reasoningId, "Thinking"), events.get(3));
    assertEquals(new SseEvent.ReasoningDelta(reasoningId, " Process"), events.get(4));
    assertEquals(new SseEvent.ReasoningEnd(reasoningId), events.get(5));
    assertEquals(new SseEvent.TextStart(start.messageId()), events.get(6));
    assertEquals(new SseEvent.TextDelta(start.messageId(), "Hello"), events.get(7));
    assertEquals(new SseEvent.TextEnd(start.messageId()), events.get(8));
    assertEquals(new SseEvent.FinishStep(), events.get(9));
    assertEquals(new SseEvent.Finish(FinishReason.STOP, Usage.empty()), events.get(10));
}

private static AssistantMessage reasoningMeta(final String text) {
    return AssistantMessage.builder().properties(Map.of("reasoningContent", text)).build();
}
```

Add to `ReasoningContentTest`:

```java
@Test
void contentFlagIsReportedForThinkingAndThought() {
    assertTrue(ReasoningContent.isContentFlagged(
            AssistantMessage.builder().content("x").properties(Map.of("thinking", true)).build()));
    assertTrue(ReasoningContent.isContentFlagged(
            AssistantMessage.builder().content("x").properties(Map.of("isThought", true)).build()));
}

@Test
void contentFlagIsFalseForStringMetadataAndPlainText() {
    assertFalse(ReasoningContent.isContentFlagged(
            AssistantMessage.builder().properties(Map.of("reasoningContent", "x")).build()));
    assertFalse(ReasoningContent.isContentFlagged(
            AssistantMessage.builder().content("hello").build()));
    assertFalse(ReasoningContent.isContentFlagged(null));
}
```

## 6. Verify

From the `khezy-boot` repo root:

```bash
./gradlew :ai:ai-elements:ai-elements-spring-ai:test
```

Consumer end-to-end (optional, in the `mcp` repo) — a thinking model should now produce
`reasoning-start … reasoning-end → text-start … text-end → finish-step → finish`.

## 7. Release / consumer wiring

- Bump `ai-elements-spring-ai` `version` to **`1.0.1`** in
  `ai/ai-elements/ai-elements-spring-ai/build.gradle` (and the matching `aiElementsModelVersion`
  consumers if any reference it).
- Publish to Maven Local (`publishToMavenLocal`) or wire the consumer via `includeBuild`.
- Consumer workaround (mutating metadata / re-implementing delta accounting in the app) is **not
  needed** once this lands; do not add it.
- `ai-elements-model` is unaffected.

## 8. Out of scope / related

- Unrelated crash fixed on the consumer side already: a no-`choices` trailing chunk from
  OpenAI-compatible providers yields a `ChatResponse` with empty generations, and
  `response.getResult()` returns `null` → NPE at `ChatResponseStreamConverter:73-74`. The consumer
  filters those chunks out before calling the converter. Consider hardening the converter with a
  null-`getResult()` guard as a separate low-risk change (not required for this bug).
- `ChatHistoryConverter.java:130` uses `ReasoningContent.extract` on stored messages; unchanged by
  this fix.
