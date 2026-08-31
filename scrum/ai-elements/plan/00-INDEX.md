# ai-elements — Spring AI integration plan (master context)

> **Read this file first.** This plan turns the research in
> `/home/khezy/Documents/learning/spring/ai-harness/references/ai-sdk/` (V4 provider spec,
> endpoint design, data models, trigger/event-id semantics) and the working mock in
> `ai-harness` into a small, maintainable KHEZY library set: a **pure model** plus a
> **Spring AI port** that converts Spring AI messages and streams into the ai-elements
> wire protocol and serves it over SSE — **both** servlet (`SseEmitter`) and WebFlux
> (`Flux<ServerSentEvent<String>>`).
>
> Each task file is self-contained: read this INDEX + the task file + the latest
> handoff entries. Context is handed off in files (`00-HANDOFF.md`), never in chat.

## 1. Purpose

Deliver `ai-elements-model` (pure protocol records, no Spring, no Reactor) and
`ai-elements-spring-ai` (Spring AI → ai-elements converters + SSE writer helpers for
servlet and WebFlux), plus a runnable servlet sample. The model lives in its own
module so the wire contract can evolve (and be published, documented, and tested)
without dragging in Spring AI — the thing the user explicitly asked for: *"define
model in separate project for easy to maintenance; port the model with spring-ai by
build-in with util for convert spring-ai message to ai-element model and helper
method for server sent event both servlet and webflux"*.

## 2. Scope analysis — what this plan delivers

| What | Where | Notes |
|---|---|---|
| Pure wire model (request + SSE response) | `ai/ai-elements/ai-elements-model` | records only + Jackson annotations for `MessagePart` polymorphism |
| Converters: ai-elements ↔ Spring AI `Message` / `ChatResponse` / `Flux<ChatResponse>` | `ai/ai-elements/ai-elements-spring-ai` | trigger handling, tool calls, usage, finish reason |
| SSE helpers — servlet `SseEmitter` + WebFlux `Flux<ServerSentEvent<String>>` | `ai/ai-elements/ai-elements-spring-ai` (`sse` package) | shared `SseEvent` model, two transports |
| Runnable sample (mock-first, DeepSeek via profile) | `ai/ai-elements/samples/ai-elements-sample` | servlet `POST /api/chat` |
| Model naming analysis | this INDEX §6 | user asked to re-analyze naming |

**Out of scope for v1** (recorded so later agents don't re-litigate):
- `LanguageModelV4*` provider-spec types (`PromptPart`, `Content`, `StreamPart`,
  `ToolChoice`, `Warning`, `AiApiException`) — those exist to build a **Path A**
  npm-style provider. This plan implements **Path B** (HTTP/SSE endpoint). If a Java
  `LanguageModelV4` provider is ever needed, it is a separate module built on
  `ai-elements-model`.
- `ai-harness`'s own persistence models (`MessageData`, `ContentBlock`, `TextBlock`,
  `ReasoningBlock`, `ToolCallBlock`) — app-level, not protocol.
- Spring Boot starter auto-configuration + WebFlux sample — deferred (see INDEX §9).

## 3. Modules & conventions (read first)

All modules under `ai/ai-elements/`, each an **isolated Gradle composite build** (own
`settings.gradle`), registered in root `settings.gradle` via `includeBuild(...)`.
Base package root: **`io.github.khezyapp.aielements.*`**. Version: **`1.0.0`**
(sample `0.0.1-SNAPSHOT`). Spring AI **`2.0.1`** (matches `a2a-spring-ai`), Spring
Boot **`4.1.0`**, Java 17 target / JDK 21 toolchain.

| Path | rootProject.name | Plugin | Coordinates / key deps |
|---|---|---|---|
| `ai/ai-elements/ai-elements-model` | `ai-elements-model` | `khezy.java-library` | `io.github.khezyapp:ai-elements-model:1.0.0`; `api com.fasterxml.jackson.core:jackson-annotations` (annotations only — stays valid under Jackson 3) |
| `ai/ai-elements/ai-elements-spring-ai` | `ai-elements-spring-ai` | `khezy.java-library` (+ `khezy.java-use-mockito` for tests) | `io.github.khezyapp:ai-elements-spring-ai:1.0.0`; `api` model, `api org.springframework.ai:spring-ai-client-chat:2.0.1`, `api org.springframework:spring-webmvc`; test: `spring-boot-starter-webmvc-test`, `reactor-test` |
| `ai/ai-elements/samples/ai-elements-sample` | `ai-elements-sample` | `khezy.springboot` | `ai-elements-spring-ai` via `includeBuild` |

Composite substitution: modules reference each other **by coordinate**; Gradle
substitutes the included build. Cross-build tasks run from repo root:
`./gradlew :ai:ai-elements:ai-elements-model:test`.

Module `settings.gradle` recipe (path from `ai/ai-elements/<module>` to repo root is
`../../..`):
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "ai-elements-model"   // or "ai-elements-spring-ai"
```

Root `settings.gradle` additions (final, AEL-01):
```groovy
includeBuild("ai/ai-elements/ai-elements-model")
includeBuild("ai/ai-elements/ai-elements-spring-ai")
includeBuild("ai/ai-elements/samples/ai-elements-sample")
```

Sample's own `settings.gradle` (standalone compilation, mirrors `examples/`):
```groovy
pluginManagement { includeBuild("../../../../build-logic") }
includeBuild("../../ai-elements-model")
includeBuild("../../ai-elements-spring-ai")
rootProject.name = "ai-elements-sample"
```

### Code style (bind in every task)
- `final` on all params and locals (`final var x = ...`); 4-space indent; 120-char
  line limit; Egyptian braces; no star imports in production code.
- Records for all data model types. `Objects.requireNonNull` for validation.
- Checkstyle is the static gate: `./gradlew :ai:ai-elements:<module>:checkstyleMain`.

## 4. Resolved decisions (design vs implementation)

| # | Decision |
|---|---|
| D1 | **One SSE transport module.** Servlet + WebFlux helpers live together in `ai-elements-spring-ai` (package `sse`). Both need `SseEvent` and both are consumed by web apps; Reactor comes transitively from `spring-ai-client-chat`, `ServerSentEvent` from `spring-web` (via webmvc). Splitting into `-servlet` / `-webflux` modules is only justified if a consumer complains about transitive deps. |
| D2 | **Model carries pre-serialized JSON strings for object payloads.** `ToolInputAvailable`/`ToolOutputAvailable` hold `String inputJson`/`String outputJson`; `Finish` holds a `Usage` record serialized inline. The model module never needs a JSON **databind** library — only `jackson-annotations` for `MessagePart` polymorphism. The spring-ai module owns the `ObjectMapper`. |
| D3 | **Drop `ToolResultPart` from the `MessagePart` union.** It is not a UI-stream part type in the AI SDK; tool results travel as `ToolInvocationPart(state="result")` (message) or SSE `tool-output-available` (stream). See §6 naming analysis. |
| D4 | **`MessagePart` polymorphism via `@JsonTypeInfo(NAME, property="type")` + `@JsonSubTypes`** (as in `ai-harness`), not `DEDUCTION` — explicit and robust. |
| D5 | **`trigger` is a plain `String`**, not an enum (protocol-defined literals `submit-message` / `regenerate-message` / `resume-stream`). Same for `ChatMessage.role` (`user`/`assistant`/`system`). Enum adds ceremony with no safety gain on the wire. |
| D6 | **Stream converter emits one text block id `"0"`** for a single text block; when reasoning precedes text, ids increment (`"0"` reasoning, `"1"` text) — matches the incrementing-counter strategy in `04-TRIGGER-AND-EVENT-ID.md`. |
| D7 | **`tool-output-available` is app-orchestrated, not auto-emitted by the stream converter.** The raw `Flux<ChatResponse>` carries tool-call **inputs** (`AssistantMessage.ToolCall`); the *output* requires executing the tool and sending a `ToolResponseMessage` in a follow-up turn. The converter emits `tool-input-start` + `tool-input-available`; the sample orchestrates output events (or defers them). |
| D8 | **Reasoning streaming is provider-dependent.** Spring AI 2.0's standard `ChatResponse` exposes text + tool-calls + finish-reason; reasoning deltas are **not** in the generic API. The model and converter **support** `reasoning-*` events (a `ReasoningDelta` can be emitted from provider metadata when present); the generic path emits none. Documented as a boundary, not a lie. |
| D9 | **Model module tests use `com.fasterxml.jackson.core:jackson-databind` (test scope)** for record round-trips. Annotations are identical under Jackson 3; a pure model module shouldn't depend on Boot to pick a databind. |

## 5. Model naming analysis (re-analyzed — user asked)

Sources: ai-harness `aisdk/model/**` (current implementation) + the AI SDK docs.

| Current (ai-harness) | Proposed | Rationale |
|---|---|---|
| package `io.github.khezyapp.aiharness.aisdk.model.*` | `io.github.khezyapp.aielements.model.*` | Drop `aiharness` (app) and `aisdk` (Vercel JS brand) — the KHEZY lib is a backend protocol implementation, not the SDK. |
| `UIMessage` | `ChatMessage` | `UI*` is frontend jargon inherited from `@ai-sdk/ui-utils`. On the wire this is just a chat message. JSON keys (`id`, `role`, `content`, `parts`) unchanged. |
| `UIPart` | `MessagePart` | Same reasoning. Sealed union of parts. |
| `TextPart`, `ReasoningPart`, `SourceUrlPart`, `FilePart`, `ToolInvocationPart`, `StepStartPart`, `StepFinishPart` | **keep names** | Their JSON `type` discriminator matches the AI SDK exactly; renaming adds churn for no wire benefit. |
| `ToolResultPart` | **removed from union** (D3) | Not a UI-stream part; tool results belong in `ToolInvocationPart` / SSE `tool-output-available`. |
| `SSEMessage` (record + static factories) | `SseEvent` (sealed interface, nested records) + static `done()` | "SSEMessage" suggests an envelope; it is one `data:` line of the stream. Typed records (not string-building factories) make the converter and tests readable and keep `type()` explicit. |
| `SseStreamBuilder` (accumulator, `build()`/`buildFlux()`/`sendTo`) | `SseEventBuilder` (transport-free accumulator producing `List<SseEvent>` + wire string) + `AiElementsSse` facade in spring-ai | Transport logic leaves the model module; model stays pure. |
| `FinishReason`, `Usage`, `Warning` (common) | `FinishReason`, `Usage` **kept**; `Warning` **dropped** | `FinishReason`/`Usage` are on the wire. `Warning` is a provider-spec type (out of scope, §2). |
| `LanguageModelV4CallOptions`, `PromptPart`, `Content`, `StreamPart`, `ToolChoice`, exceptions | **not ported** | Path A provider types. This plan is Path B (HTTP/SSE). |
| `MessageData`, `ContentBlock`, `TextBlock`, `ReasoningBlock`, `ToolCallBlock` | **not ported** | ai-harness persistence models, not protocol. |

**Wire names that must be byte-identical** (from `02-ENDPOINT-DESIGN.md` / mock):
`start`, `start-step`, `text-start`, `text-delta`, `text-end`, `reasoning-start`,
`reasoning-delta`, `reasoning-end`, `source-url`, `tool-input-start`,
`tool-input-available`, `tool-output-available`, `finish-step`, `finish`, `error`,
and the `[DONE]` sentinel. Note: `tool-input-available` is **missing** from the
current ai-harness `SSEMessage` factories — the model here adds it.

## 6. Dependency graph

```
AEL-01 (scaffold both lib modules + sample + root wiring)
  │
  ├──────► AEL-02 (ai-elements-model: records + SseEvent + tests)
  │              │
  │              └──────► AEL-03 (ai-elements-spring-ai: converters + SSE writers + tests)
  │                             │
  │                             └──────► AEL-04 (sample app + READMEs + full check + graphify)
  └────────────────────────────────────►
```

Legend: after AEL-01, AEL-02 is the only dependency chain head. AEL-03 compiles
against AEL-02's public types (verbatim in the task file). AEL-04 consumes both.
AEL-01 must land first (composite wiring + skeletons so every later task can run
`./gradlew ...:test` from root).

## 7. Task list

| # | File | Task | Builds | Depends on |
|---|---|---|---|---|
| 00 | `00-INDEX.md` | this file | — | — |
| 00b | `00-HANDOFF.md` | append-only execution log | — | — |
| 1 | `01-ai-elements-model.md` | AEL-01 scaffold (model + spring-ai + sample skeletons, root wiring) AND AEL-02 `ai-elements-model` full content + tests | `ai-elements-model` | 00 |
| 2 | `02-ai-elements-spring-ai.md` | AEL-03 `ai-elements-spring-ai`: converters + stream converter + `AiElementsSse` (servlet + WebFlux) + tests | `ai-elements-spring-ai` | 01 |
| 3 | `03-sample-app.md` | AEL-04 sample app (servlet `/api/chat`, mock + DeepSeek profile), READMEs, full `check`, graphify | `ai-elements-sample` | 02 |
| 4 | `04-webflux-and-starter.md` | AEL-05 (DEFERRED, optional): WebFlux sample + spring-boot-starter auto-config | — | 03 |

## 8. Sequencing notes

- **Critical path**: AEL-01 → AEL-02 → AEL-03 → AEL-04. There are **no parallel
  leaves** in v1 — each task's public types feed the next, so run serially.
- **Join point** = AEL-04 sample: first place both modules meet end-to-end. The
  sample's curl verification (mock JSONL vs live stream) is the parity check —
  confirm the SSE byte stream matches the reference event set exactly.
- **Final acceptance** = AEL-04 runs `./gradlew check` + `./gradlew -p
  ai/ai-elements/samples/ai-elements-sample bootRun` + curl assertions + `graphify
  update .`.

## 9. Deferred / optional (documented, not planned)

- **WebFlux sample** — the library helpers are tested via `StepVerifier` in AEL-03;
  a runnable WebFlux app is a nice-to-have follow-up (a servlet sample ships in v1).
- **`ai-elements-spring-boot-starter`** — auto-config exposing the controller +
  `ChatRequest` parsing + properties. Do after the library shape stabilizes; note the
  AGENTS.md rule: new auto-config classes go in `AutoConfiguration.imports`, never
  `@Import`.
- **Path A provider** (`LanguageModelV4` Java impl) — separate future module on top
  of `ai-elements-model` if a Java provider package is ever needed.

## 10. Cross-cutting acceptance guardrails

1. Every task: code compiles, JUnit 5 + AssertJ tests pass, Checkstyle clean on
   `src/main/java`, `final` everywhere, records for data models.
2. No new files outside the task's declared paths.
3. No logging by library code (model + spring-ai modules are libraries).
4. `SseEvent` wire output is tested character-for-character (escaping, `[DONE]`,
   `finish` with `usage`) — the AI SDK client throws on malformed deltas.
5. Spring AI message ↔ ai-elements conversions are covered by round-trip tests
   (identity/parity), not just one-directional asserts.
6. After each task: append handoff to `00-HANDOFF.md`; after AEL-04 run
   `graphify update .`.
