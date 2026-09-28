# Task 4 — AEL-05 (DEFERRED, optional): WebFlux sample + starter auto-config

> **Status: deferred.** Not in v1. Listed so the plan is complete and the work is not
> re-derived later. Execute only on explicit request. If skipped, leave this file as
> the design note for whoever picks it up.

## Objective (when executed)

1. **WebFlux sample** — a second runnable app (or a `webflux` profile in the existing
   sample) using `AiElementsSse.streamChat(...)` returning
   `Flux<ServerSentEvent<String>>` from a `@RestController` with
   `produces = "text/event-stream"`. Proves the WebFlux helper end to end.
2. **`ai-elements-spring-boot-starter`** — auto-config that:
   - registers a `ChatController`-style endpoint wiring `ChatRequest` → Spring AI
     `ChatClient` → SSE;
   - exposes `AiElementsProperties` (path, chat-client bean name, streaming timeout,
     cors origins);
   - is gated with `@ConditionalOnClass` / `@ConditionalOnProperty`.
   - **Mandatory AGENTS.md rule**: new auto-config classes go in
     `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
     **never** in `@Import` with conditionally-gated classes.

## Design notes (for future agent)

- Module path `ai/ai-elements/ai-elements-spring-boot-starter`, plugin
  `khezy.springboot-library`. Coordinates `io.github.khezyapp:ai-elements-spring-boot-starter:1.0.0`.
- The starter's controller depends on a `ChatClient` bean the **user** provides
  (`@ConditionalOnBean(ChatClient.class)` for the endpoint; do not auto-build a model).
- Keep the library modules dependency-free of the starter (starter depends on them,
  not the reverse).
- WebFlux sample module: `ai/ai-elements/samples/ai-elements-webflux-sample`
  (`khezy.springboot`), depends on `ai-elements-spring-ai`; controller returns
  `Flux<ServerSentEvent<String>>`.
- Add `./gradlew -p ai/ai-elements/samples/ai-elements-webflux-sample bootRun` +
  curl verification to the acceptance flow.

## Acceptance criteria (when executed)

Same bar as AEL-04 plus: `AutoConfiguration.imports` verified (not `spring.factories`,
not `@Import`), `@ConditionalOnMissingBean` on every overridable bean, full `check`
green, `graphify update .`.

## Hand-off to next task

Append a handoff entry noting: user explicitly requested this work; WebFlux sample +
starter both verified; any deviations from the notes above.

### Hand-off 2026-09-01 (Objective 1 only)
- **User explicitly requested this work, scoped to Objective 1 (WebFlux sample) only.**
  The `ai-elements-spring-boot-starter` (Objective 2) remains out of scope and is NOT
  implemented; leave this file as the design note for a future agent who picks it up.
- WebFlux sample created at `ai/ai-elements/samples/ai-elements-webflux-sample`
  (`khezy.springboot`), registered in root `settings.gradle`. Runs on **Netty**
  (reactive) and proves `AiElementsSse.streamChat(...)` (`Flux<ServerSentEvent<String>>`)
  end to end in `mock` profile; smoke test passes, `check` green.
- **Deviations from the notes above (WebFlux reactive-mode gotchas):**
  1. `ai-elements-spring-ai` exposes `spring-webmvc` and `jakarta.servlet-api` as `api`
     deps, so a WebFlux consumer must exclude both from the runtime classpath
     (`spring-webmvc`, `jakarta.servlet-api`) **and** exclude the
     `spring-boot-starter-webmvc` that the `khezy.springboot` convention plugin adds via
     `configurations.all`, or Spring Boot detects servlet mode and starts Tomcat instead
     of Netty.
  2. Boot 4.1 `@AutoConfigureWebTestClient` lives in
     `org.springframework.boot.webtestclient.autoconfigure` (module
     `spring-boot-webtestclient`); the test uses `spring-boot-starter-webflux-test`, not
     `spring-boot-starter-test`.
  3. In `WebTestClient`, `text/event-stream` responses are decoded — each body element is
     the event **data** (no `data:` prefix); assert on `{"type":"start"}` and `[DONE]`.
