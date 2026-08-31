# Handoff log — ai-elements Spring AI integration

> **Append-only.** One entry per completed task, in order. Never edit or remove
> earlier entries. The log tail is the only context a later task agent may assume;
> every task file's "Hand-off to next task" lists exactly what to record here.

---

## Task 1 — AEL-01 scaffold + AEL-02 ai-elements-model — DONE

- **Date / agent**: 2026-09-01 / <agent id>
- **Verified**:
  - `./gradlew :ai-elements-model:test :ai-elements-model:checkstyleMain :ai-elements-model:checkstyleTest` → BUILD SUCCESSFUL (20 test cases, Checkstyle clean)
  - `./gradlew :ai-elements-model:check` → BUILD SUCCESSFUL
  - `./gradlew :ai-elements-spring-ai:check :ai-elements-sample:check` → BUILD SUCCESSFUL (empty skeletons)
  - `./gradlew projects` → all three `ai-elements` builds listed
  - `./gradlew :ai-elements-model:dependencies --configuration runtimeClasspath` → only `com.fasterxml.jackson.core:jackson-annotations:2.18.2`; **zero Spring / Reactor deps**
- **Files created**:
  - `ai/ai-elements/ai-elements-model/settings.gradle` — model module build
  - `ai/ai-elements/ai-elements-model/build.gradle` — `khezy.java-library`, group `io.github.khezyapp`, version `1.0.0`; `api jackson-annotations`, test `jackson-databind`
  - `ai/ai-elements/ai-elements-spring-ai/settings.gradle` + `build.gradle` — spring-ai skeleton (no deps yet; AEL-03 adds them), plugin `khezy.java-library`
  - `ai/ai-elements/samples/ai-elements-sample/settings.gradle` + `build.gradle` — sample skeleton, plugin `khezy.springboot`, version `0.0.1-SNAPSHOT`
  - `ai/ai-elements/ai-elements-model/src/main/java/io/github/khezyapp/aielements/model/request/*` — `ChatRequest`, `ChatMessage`, `MessagePart` (sealed interface), `TextPart`, `ReasoningPart`, `SourceUrlPart`, `FilePart`, `ToolInvocationPart`, `StepStartPart`, `StepFinishPart`
  - `ai/ai-elements/ai-elements-model/src/main/java/io/github/khezyapp/aielements/model/common/*` — `FinishReason` (enum), `Usage` (record)
  - `ai/ai-elements/ai-elements-model/src/main/java/io/github/khezyapp/aielements/model/response/*` — `SseEvent` (sealed interface), `SseEventBuilder`
  - `ai/ai-elements/ai-elements-model/src/test/java/**` — `ChatRequestJsonRoundTripTest`, `SseEventWireFormatTest`, `SseEventBuilderTest`, `FinishReasonTest`
- **Files edited**:
  - `settings.gradle` (root) — appended the three `includeBuild(...)` lines for the ai-elements modules
  - `scrum/ai-elements/plan/00-HANDOFF.md` — this entry
- **Public surface added** (types + signatures, verbatim as-built):
  - `request.ChatRequest(String id, List<ChatMessage> messages, String trigger, String messageId)`
  - `request.ChatMessage(String id, String role, String content, List<MessagePart> parts)`
  - `request.MessagePart` — sealed interface, **permitted list verbatim**: `TextPart, ReasoningPart, SourceUrlPart, FilePart, ToolInvocationPart, StepStartPart, StepFinishPart` (7 types, `ToolResultPart` NOT present — D3). Annotated `@JsonTypeInfo(use = Id.NAME, property = "type")` + `@JsonSubTypes` (names: `text`, `reasoning`, `source-url`, `file`, `tool-invocation`, `step-start`, `step-finish`).
  - Part records: `TextPart(String type, String text)`, `ReasoningPart(String type, String text)`, `SourceUrlPart(String type, String url, String title)`, `FilePart(String type, String name, String mimeType, String data)`, `ToolInvocationPart(String type, String toolCallId, String toolName, String state, Map<String,Object> args, Object result)`, `StepStartPart(String type)`, `StepFinishPart(String type)`. Every part's first field is `String type` matching the discriminator.
  - `common.FinishReason` enum: `STOP("stop")`, `LENGTH("length")`, `CONTENT_FILTER("content-filter")`, `TOOL_CALLS("tool-calls")`, `ERROR("error")`, `OTHER("other")`; methods `String value()`, `static FinishReason fromString(String)` (falls back to `OTHER` on unknown/null).
  - `common.Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens, Integer reasoningTokens, Integer cachedInputTokens)` with `static Usage empty()` = `new Usage(0, 0, 0, null, null)`.
  - `response.SseEvent` — sealed interface, permits (verbatim): `Start, StartStep, TextStart, TextDelta, TextEnd, ReasoningStart, ReasoningDelta, ReasoningEnd, SourceUrl, ToolInputStart, ToolInputAvailable, ToolOutputAvailable, FinishStep, Finish, ErrorEvent`. Members: `String type()`, `String json()`, `default String toWireFormat()` (`"data: " + json() + "\n\n"`), `static String done()` (`"data: [DONE]\n\n"`), private `static String escape(String)`.
  - `response.SseEventBuilder` — fluent: `start(String)`, `startStep()`, `text(String,String)` (start+delta+end), `textDelta(String,String)`, `reasoning(String,String)` (start+delta+end), `sourceUrl(String,String,String)`, `toolInputStart(String,String)`, `toolInputAvailable(String,String,String)`, `toolOutputAvailable(String,String)`, `finish(FinishReason, Usage)` (finish-step + finish), `error(String)`, `List<SseEvent> build()` (immutable ordered copy), `String buildWireFormat()` (events + `[DONE]`).
- **SseEvent `json()` field order (exact, as built — AEL-03 must match when constructing)**:
  - `start` → `{"type":"start"}` (+ `,"messageId":"<escaped>"` when non-null; **omit-when-null kept** as planned)
  - `start-step` → `{"type":"start-step"}`
  - `text-start` → `{"type":"text-start","id":"<esc>"}`
  - `text-delta` → `{"type":"text-delta","id":"<esc>","delta":"<esc>"}`
  - `text-end` → `{"type":"text-end","id":"<esc>"}`
  - `reasoning-start` / `reasoning-delta` / `reasoning-end` → same shape as their `text-*` counterparts (ids and deltas escaped)
  - `source-url` → `{"type":"source-url","sourceId":"<esc>","url":"<esc>"}` (+ `,"title":"<esc>"` when non-null; **title omitted when null**)
  - `tool-input-start` → `{"type":"tool-input-start","toolCallId":"<esc>","toolName":"<esc>"}`
  - `tool-input-available` → `{"type":"tool-input-available","toolCallId":"<esc>","toolName":"<esc>","inputJson":<raw>}` (inputJson embedded raw, unescaped)
  - `tool-output-available` → `{"type":"tool-output-available","toolCallId":"<esc>","output":<raw>}` — **key is `output`** (matches ai-harness reference), value raw
  - `finish-step` → `{"type":"finish-step"}`
  - `finish` → `{"type":"finish","finishReason":"<value>","usage":{"inputTokens":N,"outputTokens":N,"totalTokens":N}}` + `,"reasoningTokens":N` + `,"cachedInputTokens":N` appended (in that order) only when non-null
  - `error` → `{"type":"error","errorText":"<esc>"}`
  - `escape(String)` handles: `\\` → `\\\\`, `\"` → `\\"`, `\n` → `\\n`, `\r` → `\\r`, `\t` → `\\t`; `null` → `""` (empty, unquoted)
- **Gotchas / decisions** (real shapes, not planned ones):
  - **AssertJ is NOT on the test classpath** for pure `khezy.java-library` modules. AssertJ comes only transitively via Spring Boot's `spring-boot-starter-webmvc-test` (Spring modules). The task said "JUnit 5 + AssertJ" but for this Spring-free module tests use **JUnit 5 `Assertions`** (same as `ai/a2a/a2a-core`). Do NOT add an AssertJ dependency — it would pull in a Spring/Boot BOM. Use `org.junit.jupiter.api.Assertions.*`.
  - **No dependency-management BOM in `khezy.java-library`** — jackson deps need explicit versions. As built: `api com.fasterxml.jackson.core:jackson-annotations:2.21`, `testImplementation com.fasterxml.jackson.core:jackson-databind:2.21.4` (annotations version as bare `2.21`; databind uses patch `2.21.4`).
  - **Jackson 2 vs Boot-4 Jackson 3 — the version is `2.21`, NOT `2.18.2`**. Jackson 3 did NOT move the annotations namespace/artifact: `tools.jackson.core:jackson-databind:3.1.4` (Boot 4's default) still depends on `com.fasterxml.jackson.core:jackson-annotations:2.21` (single shared artifact/package). Verified via `./gradlew :api-security:dependencyInsight --dependency jackson-annotations` → resolves to **2.21**. Boot 4's classpath also carries `com.fasterxml.jackson.core:jackson-databind:2.21` (Jackson 2) alongside `tools.jackson.core:jackson-databind:3.1.x` (Jackson 3) — different packages, no clash. So: (a) hard-pinning `2.18.2` would be a downgrade vs the Boot-4 graph (Gradle picks 2.21 by default so it doesn't break, but it's fragile skew); (b) annotating with `com.fasterxml.jackson.annotation.*` is forward-compatible with Jackson 3. Keep the model on `2.21` to match the ecosystem. The test-scope `jackson-databind:2.21` is Jackson 2 — fine for round-trip tests; annotations are identical under Jackson 2/3 (per D9).
  - **Composite-build task path differs from the task file**: `./gradlew :ai:ai-elements:ai-elements-model:test` FAILS with "project 'ai' is ambiguous" (each module is a separate included build, not nested projects). Correct invocation is `./gradlew :ai-elements-model:test` (and `:ai-elements-model:checkstyleMain`), matching how `:a2a-core:test` is run. Update acceptance commands accordingly.
- **Next task(s) must know**:
  - AEL-03 (`ai-elements-spring-ai`) constructs these `SseEvent` nested records directly (e.g. `new SseEvent.TextDelta("0", delta)`) — must use the exact field order above. Use `new SseEvent.X(...)` constructors, not a factory.
  - The model module is intentionally dependency-light; the spring-ai module owns the `ObjectMapper` (D2). Do not add Jackson databind to the model's compile/runtime scope.
  - `tool-input-available` is the new event added in this module (missing from ai-harness `SSEMessage`). Its JSON key is `inputJson` (raw). `tool-output-available` uses key `output` (raw) — ported from ai-harness.
  - `finish` never renders a `null` usage; if no usage is available use `Usage.empty()` (renders `0,0,0`).
  - For AEL-03 build.gradle: `ai-elements-model` dep will be `api "io.github.khezyapp:ai-elements-model:1.0.0"` (composite substitution) plus spring-ai-client-chat etc. per INDEX §3.

---

## Task 2 — AEL-03 `ai-elements-spring-ai`: converters + stream converter + SSE facade — DONE

- **Date / agent**: 2026-09-01 / <agent id>
- **Verified**:
  - `./gradlew :ai-elements-spring-ai:check` → BUILD SUCCESSFUL (19 test cases green, Checkstyle clean on main + test)
  - `./gradlew :ai-elements-spring-ai:test` → BUILD SUCCESSFUL (4 test classes: ChatRequestConverterTest 5, ChatResponseConverterTest 5, ChatResponseStreamConverterTest 4, AiElementsSseTest 5)
- **Files created**:
  - `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatRequestConverter.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatResponseConverter.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/convert/ChatResponseStreamConverter.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/main/java/io/github/khezyapp/aielements/springai/sse/AiElementsSse.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/test/java/io/github/khezyapp/aielements/springai/convert/ChatRequestConverterTest.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/test/java/io/github/khezyapp/aielements/springai/convert/ChatResponseConverterTest.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/test/java/io/github/khezyapp/aielements/springai/convert/ChatResponseStreamConverterTest.java`
  - `ai/ai-elements/ai-elements-spring-ai/src/test/java/io/github/khezyapp/aielements/springai/sse/AiElementsSseTest.java`
- **Files edited**:
  - `ai/ai-elements/ai-elements-spring-ai/build.gradle` — added `khezy.java-use-mockito` plugin + full dependencies (see below)
  - `scrum/ai-elements/plan/00-HANDOFF.md` — this entry
- **Public surface added** (types + signatures, verbatim as-built):
  - `convert.ChatRequestConverter` (final, private ctor): `static List<Message> toSpringAiMessages(ChatRequest)`; `static List<ChatMessage> filterForTrigger(ChatRequest)`; `static Message toSpringAiMessage(ChatMessage)`. Role dispatch: `"system"`→`SystemMessage`, `"user"`→`UserMessage` (text-only), `"assistant"`→`AssistantMessage` (+ `ToolCall` from `ToolInvocationPart(state="call")`), `"tool"`→`ToolResponseMessage` (from `state="result"`), unknown role → `IllegalArgumentException`.
  - `convert.ChatResponseConverter` (final, private ctor): `static ChatMessage toChatMessage(ChatResponse, String id)`; `static Usage toUsage(org.springframework.ai.chat.metadata.Usage)`; `static FinishReason toFinishReason(String)`; `static List<MessagePart> toParts(AssistantMessage)`.
  - `convert.ChatResponseStreamConverter` (final, private ctor): `static Flux<SseEvent> toEvents(Flux<ChatResponse>)`; `static Flux<SseEvent> toEvents(Flux<ChatResponse>, String textBlockId)`. Default text block id `"0"`. `[DONE]` NOT emitted (D6/D7/D8 rules locked).
  - `sse.AiElementsSse` (final, private ctor): `static String toWireFormat(List<SseEvent>)`; `static List<String> toWireLines(List<SseEvent>)`; `static SseEmitter writeTo(SseEmitter, List<SseEvent>)`; `static SseEmitter writeTo(SseEmitter, Flux<SseEvent>)`; `static SseEmitter streamToEmitter(Flux<ChatResponse>, long timeout)`; `static Flux<ServerSentEvent<String>> toServerSentEvents(List<SseEvent>)`; `static Flux<ServerSentEvent<String>> toServerSentEvents(Flux<SseEvent>)`; `static Flux<ServerSentEvent<String>> streamChat(Flux<ChatResponse>)`; `static void applyStreamHeaders(HttpServletResponse)` (Cache-Control no-cache, X-Accel-Buffering no, X-Vercel-AI-UI-Message-Stream v1, content-type text/event-stream).
- **Gotchas / decisions** (real shapes, not planned ones):
  - **ObjectMapper decision**: Jackson 3 (`tools.jackson.databind.ObjectMapper`) — available transitively on the compile classpath via `spring-ai-commons:2.0.1` → `tools.jackson.core:jackson-databind:3.1.5` (no explicit dep needed). Each converter holds its own `private static final ObjectMapper MAPPER` (thread-safe, stateless). Confirmed `tools.jackson.databind.ObjectMapper.writeValueAsString(Object)` and `readValue(String, TypeReference<T>)` signatures via `javap`.
  - **No dependency-management BOM** in `khezy.java-library` — ALL non-BOM deps must be version-pinned. As built: `api org.springframework:spring-webmvc:7.0.9`, `api jakarta.servlet:jakarta.servlet-api:6.1.0`, `testImplementation org.springframework.boot:spring-boot-starter-webmvc-test:4.1.0`, `testImplementation io.projectreactor:reactor-test:3.8.6`. (spring-webmvc with no version FAILED resolution; unsuffixed starter/reactor-test also FAILED.)
  - **Spring AI 2.0.1 `UserMessage.builder().media(...)` NOT used in v1** — user messages emit text-only (FilePart media handling deferred; would need `org.springframework.util.MimeType` + `org.springframework.ai.content.Media`). Documented as a v1 limitation.
  - **`AssistantMessage` tool calls require the builder**, not the public `(String)` ctor: `AssistantMessage.builder().content(text).toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, argsJson))).build()` (the `(String,Map,List,List)` ctor is `protected`). `new AssistantMessage(String)` is text-only.
  - **`ToolResponseMessage` has only a protected ctor** — use `ToolResponseMessage.builder().responses(List.of(new ToolResponseMessage.ToolResponse(id, name, responseData))).build()`.
  - **`Generation` has real public ctors** — for tests used `new Generation(AssistantMessage)` and `new Generation(AssistantMessage, ChatGenerationMetadata.builder().finishReason("stop").build())`. `ChatResponse` ctor `new ChatResponse(List<Generation>, ChatResponseMetadata)`; `ChatResponseMetadata.builder().usage(new DefaultUsage(p,c,t)).build()`; `DefaultUsage` is in `org.springframework.ai.chat.metadata` (NOT `messages`).
  - **`ChatGenerationMetadata` is an interface** with static `NULL` constant; `getFinishReason()` returns `String` (null when absent). Stream converter gates finish emission on non-null/non-empty finish reason.
  - **`toSpringAiMessages`/`filterForTrigger` do NOT carry the ChatMessage id** into Spring AI metadata — regenerating trims by index lookup on `id()`; the id is not preserved on the converted message (acceptable for v1).
  - **`SseEmitter` test strategy (confirmed)**: `ResponseBodyEmitter.initialize(...)` is now **package-private** in Spring Framework 7 (takes an internal `Handler`, not `AsyncWebRequest`), so the `MockHttpServletResponse` + `StandardServletAsyncWebRequest` mock-async approach is NOT usable from the test package. Relied on the **wire-lines / wire-format pure helpers + `applyStreamHeaders` via `MockHttpServletResponse`** for servlet coverage; the emissio/sequence logic is fully covered by the WebFlux `StepVerifier` path which shares the same `SseEvent` serialization.
  - **Stream converter uses `Flux.defer`** so per-subscription state (open-text-block flag, `StreamState` inner class holding `id` + `textBlockOpen`) is isolated; prefix `Start(null)`+`StartStep()` emitted once via `Flux.concat`. `concatMap` per response wraps the `eventsFor` list in `Flux.fromIterable`.
  - **`reactor-test` verification**: `StepVerifier` + `consumeNextWith` used; `List.of(Start, StartStep)` needs explicit `List.<SseEvent>of(...)` or a typed `List<SseEvent>` variable (Java type inference fails across two distinct record types).
- **Next task(s) must know** (AEL-04 sample):
  - Composite-build task path is `./gradlew :ai-elements-spring-ai:check` (NOT `:ai:ai-elements:...`), and dependencies must be version-pinned (no BOM).
  - `AiElementsSse.streamChat(Flux<ChatResponse>)` is the one-call sample entrypoint; `applyStreamHeaders(HttpServletResponse)` must be called on the controller response before streaming. Servlet path uses `streamToEmitter(responses, timeout)` + MVC `SseEmitter` return; WebFlux path uses `streamChat(...)` + `Flux<ServerSentEvent<String>>` return.
  - A curl-able stream is still NOT available — that is wired by AEL-04 (the sample module).

## Task 3 — AEL-04 `ai-elements-sample`: runnable servlet app — DONE

- **Date / agent**: 2026-09-01 / <agent id>
- **Verified**:
  - `./gradlew checkstyleMains checkstyleTests` → BUILD SUCCESSFUL (all modules)
  - `./gradlew :ai-elements-model:check :ai-elements-spring-ai:check :ai-elements-sample:check` → BUILD SUCCESSFUL
  - `:ai-elements-sample:bootJar` → BUILD SUCCESSFUL; appears in `./gradlew projects` as included build `:ai-elements-sample`
  - Mock boot (`java -jar ... --spring.profiles.active` default=mock) → "Started AiElementsSampleApplication"; **parity curl PASS** (headers + full event set + `[DONE]`)
  - `:ai-elements-sample:test` → 1 smoke test green (`AiElementsSampleSmokeTest`, `@SpringBootTest` + `MockMvc`, mock profile, 200 + text/event-stream + `[DONE]`)
  - DeepSeek profile boot (fake key) → app starts, controller's `ChatService` path active, `start`+`start-step` envelope emitted then real DeepSeek 401 (expected without real key) → wiring proven
  - `graphify update .` → 4317 nodes / 8786 edges rebuilt
- **Mock strategy chosen**: **canned `List<SseEvent>` read from a trimmed fixture `mock/complex-workflow-stream.jsonl`**; the controller has a mock-only branch calling `AiElementsSse.writeTo(emitter, events)`. Picked because the reference stream contains `reasoning-*` and `tool-output-available` events that the generic `Flux<ChatResponse>` converter (`streamToEmitter`) cannot emit.
- **Files created**:
  - `ai/ai-elements/samples/ai-elements-sample/build.gradle` (+`settings.gradle` pre-existing)
  - `.../src/main/java/io/github/khezyapp/aielements/sample/AiElementsSampleApplication.java`
  - `.../controller/ChatController.java`
  - `.../service/ChatService.java` (interface), `DeepSeekChatService.java` (`@Profile("deepseek")`), `MockChatService.java` (`@Profile("mock")`)
  - `.../config/CorsConfig.java`
  - `.../src/main/resources/application.yaml`, `application-mock.yaml`, `application-deepseek.yaml`, `mock/complex-workflow-stream.jsonl`
  - `.../src/test/java/.../AiElementsSampleSmokeTest.java`
  - READMEs: `ai/ai-elements/README.md`, `samples/ai-elements-sample/README.md`, `ai-elements-model/README.md`, `ai-elements-spring-ai/README.md`
- **Controller/service shape (as-built)**:
  - `ChatController` `@RestController @RequestMapping("/api/chat")`; ctor with `@Autowired(required=false) ChatService` + `@Autowired(required=false) MockChatService`; `@PostMapping(produces="text/event-stream") SseEmitter chat(@RequestBody ChatRequest, HttpServletResponse)`. Calls `AiElementsSse.applyStreamHeaders(response)`. Mock branch (MockChatService present) → `writeTo(new SseEmitter(120_000L), mockService.events())`; else → `writeTo(new SseEmitter(120_000L), chatService.stream(request))`.
  - `MockChatService` loads `mock/complex-workflow-stream.jsonl` via `ClassPathResource`, parses each JSON line with Jackson 3 `tools.jackson.databind.ObjectMapper` into `SseEvent`, exposes `List<SseEvent> events()`.
  - `DeepSeekChatService` builds `ChatClient` from `ChatClient.Builder` bean, `chatService.stream(request)` → `Flux<ChatResponse>` via `ChatRequestConverter.toSpringAiMessages(...)` + `.prompt().messages(messages).stream().chatResponse()`.
- **Spring Boot 4 gotchas hit (important for AEL-05)**:
  - **`@AutoConfigureMockMvc`/`@WebMvcTest` moved package** in 4.1.0 to `org.springframework.boot.webmvc.test.autoconfigure` (NOT `org.springframework.boot.test.autoconfigure.web.servlet` — that package no longer exists; `spring-boot-test-autoconfigure` now holds only json/jdbc). Added to AGENTS.md.
  - **`asyncDispatch(MvcResult)` is NOT on `MockMvc`** — it is a static method of `org.springframework.test.web.servlet.request.MockMvcRequestBuilders` (import statically). Added to AGENTS.md.
  - **`AiElementsSse.writeTo` servlet overloads MUST append `data:[DONE]` before `emitter.complete()`** — the first parity run showed the stream missing `[DONE]` (client hangs). Fixed both `writeTo(SseEmitter, List<SseEvent>)` and `writeTo(SseEmitter, Flux<SseEvent>)` in `ai-elements-spring-ai`. Added to AGENTS.md + existing AGENTS.md `AiElementsSse.writeTo` note.
  - **DeepSeek auto-config fails at startup in mock mode with empty key**: `DeepSeekChatAutoConfiguration` is `@ConditionalOnProperty(spring.ai.model.chat, havingValue="deepseek", matchIfMissing=true)` and asserts the API key on `deepSeekApi()`. Fix: `application-mock.yaml` excludes `org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration`; `application-deepseek.yaml` sets `spring.ai.model.chat: deepseek`.
  - **Jackson 3**: `JsonNode.asText()` deprecated → use `asString()`.
  - **Spring Boot 4**: sample uses `spring-boot-starter-webmvc` (NOT `starter-web`); `spring-ai-starter-model-deepseek:2.0.1` + `spring-ai-bom:2.0.1` resolve from Maven Central (`ext { springAiVersion = '2.0.1' }` + `dependencyManagement { imports { mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}" } }`).
- **Parity curl output (mock, the join point)**:
  - Headers: `HTTP/1.1 200`, `Cache-Control: no-cache`, `X-Accel-Buffering: no`, `X-Vercel-AI-UI-Message-Stream: v1`, `Content-Type: text/event-stream`.
  - Stream (16 `data:` lines: 15 events + `[DONE]`): `start` → `start-step` → `reasoning-start`/`reasoning-delta`/`reasoning-end` → `tool-input-start` → `tool-output-available` → `finish-step` → `start-step` → `text-start`/`text-delta`x2/`text-end` → `finish-step` → `finish` (`finishReason:"stop"`, usage) → `data:[DONE]`.
- **Hand-off to AEL-05 (starter)**: candidate auto-config surface — a `ai-elements-spring-boot-starter` should expose `@ConditionalOnClass(ChatClient)` + `@ConditionalOnMissingBean` a `ChatService`-style streaming bean, auto-add an `/api/chat` controller behind a property flag (e.g. `io.github.khezyapp.ai-elements.enabled`), and reuse `AiElementsSse.writeTo`/`applyStreamHeaders` (already `[DONE]`-correct). Recommend copying the `ChatController` + `applyStreamHeaders` from this sample verbatim.

