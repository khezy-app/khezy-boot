# Task 3 — AEL-04 `ai-elements-sample`: runnable servlet app

## Objective

Ship a runnable **servlet** Spring Boot app that exposes `POST /api/chat` returning
the ai-elements UI-message-stream SSE bytes — **mock-first** (a replay of a reference
`.jsonl` proves the wire format byte-for-byte), then **DeepSeek via profile** to prove
the real Spring AI → ai-elements pipeline end to end. This is the join point where
both library modules meet; its curl verification is the parity check for the whole
plan.

## Hand-off context

- **Contract**: INDEX §3 (module recipe, sample settings.gradle), §8 (join point /
  parity), AGENTS.md "Example modules" + "Example settings.gradle" + "data.sql / DDL"
  gotchas only if DB is used (it is not). Spring Boot 4 / Jackson 3 notes from AGENTS.md.
- **What NOT to re-explore**: model + spring-ai module sources (handoffs from tasks
  1–2 have signatures). `AiElementsSse.streamToEmitter` / `streamChat` are the entry
  points to use.
- **Reference wire bytes**: `ai-harness/src/main/resources/mock/complex-workflow-stream.jsonl`
  and `coding-agent-stream.jsonl` — replay a subset as the mock mode.

## Design notes

- Two active profiles:
  - default / `mock` — replay a canned `SseEvent` sequence (matches the reference
    event set: `start`, `start-step`, `reasoning-*`, `tool-input-start`,
    `tool-output-available`, `text-*`, `finish-step`, `finish`, `[DONE]`).
  - `deepseek` — `ChatClient` (DeepSeek, `spring-ai-starter-model-deepseek`) →
    `.stream().chatResponse()` → `AiElementsSse.streamToEmitter(...)`. System prompt +
    simple tools optional; keep minimal.
- The sample is `khezy.springboot` (bootJar enabled). Spring Boot 4 → use
  `spring-boot-starter-webmvc` (NOT the old `starter-web`).
- CORS: the frontend (ai-elements-vue on `localhost:5173`) needs it; use a
  `WebMvcConfigurer` addCorsMappings (as in `ai-harness` `CorsConfig`), or the
  `@CrossOrigin` on the controller. Keep one, not both.

## Files to create

### `ai/ai-elements/samples/ai-elements-sample/settings.gradle`
```groovy
pluginManagement { includeBuild("../../../../build-logic") }
includeBuild("../../ai-elements-model")
includeBuild("../../ai-elements-spring-ai")
rootProject.name = "ai-elements-sample"
```

### `ai/ai-elements/samples/ai-elements-sample/build.gradle`
```groovy
plugins {
    id("khezy.springboot")
}
group = "io.github.khezyapp"
version = "0.0.1-SNAPSHOT"
ext { springAiVersion = '2.0.1' }
dependencies {
    implementation "io.github.khezyapp:ai-elements-spring-ai:1.0.0"
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.ai:spring-ai-starter-model-deepseek'
}
dependencyManagement {
    imports { mavenBom "org.springframework.ai:spring-ai-bom:${springAiVersion}" }
}
```

### `src/main/java/io/github/khezyapp/aielements/sample/...`
- `AiElementsSampleApplication.java` — `@SpringBootApplication`.
- `controller/ChatController.java`:
  ```java
  @RestController
  @RequestMapping("/api/chat")
  public class ChatController {
      @PostMapping(produces = "text/event-stream")
      public SseEmitter chat(@RequestBody ChatRequest request, HttpServletResponse response) {
          AiElementsSse.applyStreamHeaders(response);
          return AiElementsSse.streamToEmitter(chatService.stream(request), 120_000L);
      }
  }
  ```
- `service/ChatService.java` (interface) + `MockChatService` (`@Profile("mock")`,
  default) + `DeepSeekChatService` (`@Profile("deepseek")`). Each returns
  `Flux<ChatResponse>`; mock wraps a canned sequence in a synthetic `ChatResponse`
  **or** — simpler and just as valid for parity — the mock returns a pre-built
  `List<SseEvent>` and the controller has a mock-only branch calling
  `AiElementsSse.writeTo(emitter, events)`. Log which you pick (parity goal is the
  same: the bytes match the reference `.jsonl`).
- `config/CorsConfig.java` — `WebMvcConfigurer` CORS mapping (allow
  `http://localhost:5173`).
- `application.yaml`:
  ```yaml
  spring:
    application: { name: ai-elements-sample }
    profiles: { active: mock }
    ai:
      deepseek:
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-v4-flash
  ```
- `src/main/resources/mock/` — a small `.jsonl` fixture copied from
  `ai-harness` `complex-workflow-stream.jsonl` (trim to ~10 lines) used by
  `MockChatService`.

## Tests / verification (no unit tests required — this is a runnable app, but wire a smoke test if cheap)

### Manual parity check (the join point)
```bash
./gradlew -p ai/ai-elements/samples/ai-elements-sample bootRun
# in another shell:
curl -sN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{
    "id":"chat_1",
    "messages":[{"id":"m1","role":"user","content":"hello",
      "parts":[{"type":"text","text":"hello"}]}],
    "trigger":"submit-message","messageId":null}'
```
Assert:
1. Response headers include `Content-Type: text/event-stream`, `Cache-Control:
   no-cache`, `X-Vercel-AI-UI-Message-Stream: v1`.
2. Stream lines match the reference event set exactly (`data: {...}` per event,
   ending `data: [DONE]`).
3. DeepSeek profile: `./gradlew -p ai/ai-elements/samples/ai-elements-sample bootRun
   --args='--spring.profiles.active=deepseek'` with `DEEPSEEK_API_KEY` set → same
   event envelope with live text deltas (id `"0"`).

Optionally add one `@SpringBootTest` + `MockMvc` smoke test asserting the mock stream
returns 200 + `text/event-stream` + a `[DONE]` terminal line, if `spring-boot-starter-webmvc-test`
resolves cleanly for the sample.

## READMEs

Write with KHEZY tone (AGENTS.md "Documentation Tone"):
- `ai/ai-elements/README.md` — module table, quick start, "what this does NOT do"
  (no Path A provider, no starter yet, tool-output orchestrated by the app), "who
  it's for".
- `ai/ai-elements/samples/ai-elements-sample/README.md` — run commands + curl.
- Optionally a short `ai/ai-elements/ai-elements-model/README.md` + `.../spring-ai/README.md`
  mirroring a2a's per-module READMEs.

## Acceptance criteria

```bash
./gradlew :ai:ai-elements:ai-elements-sample:bootJar       # compiles
./gradlew check                                            # full repo check (all modules incl. samples)
./gradlew checkstyleMains checkstyleTests
./gradlew -p ai/ai-elements/samples/ai-elements-sample bootRun   # boots; curl passes parity
cd /mnt/data/khezylib/khezy-boot && graphify update .
```
All green + curl parity confirmed. Sample is registered in root `settings.gradle`
(done in AEL-01) and appears in `./gradlew projects`.

## Hand-off to next task

Log into `00-HANDOFF.md`:
- Which mock strategy was used (canned `SseEvent` list vs synthetic `ChatResponse`).
- Exact controller/service shapes; whether `writeTo` or `streamToEmitter` was used.
- Any Spring Boot 4 gotchas hit (starter names, CORS, SSE content-type).
- Confirm the parity curl output (paste the header + first/last stream lines).
- If no `ai-elements-spring-boot-starter` work follows immediately, note the
  candidate auto-config surface for AEL-05 (deferred).
