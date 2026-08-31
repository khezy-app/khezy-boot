# A2A Core Library — v1 Action Plan (Master Context)

> **Read this file first.** Every task file in `scrum/a2a/v1-actions/` references this
> document. A task is self-contained when you have read: (1) this file, (2) the task
> file itself, (3) the LAST handoff entry per prerequisite task in `99-handoff-log.md`.
> You must NOT need to re-explore source code to implement a task — the context is
> preserved in these files.
>
> **Context is handed off in files, never in chat** — see §6.7 for the binding rule.

## 1. Goal

Implement the **A2A contract layer** designed in `../a2a-core-library-design.md`
(attached to this repo, draft v3): a set of port interfaces + default implementations
+ a Spring Boot starter, built **on top of the official A2A Java SDK**. The SDK's spec
types are the canonical wire model — we abstract *behavior* (execution, storage,
dispatch, client calls, identity), never the wire model.

## 2. Scope analysis — how the plan decomposes

The design doc's §8 implementation plan (Phase 0–5) is broken into **9 agent-executable
tasks**, ordered so that each task produces something compilable/testable on its own:

| Design phase | v1 task(s) |
|---|---|
| Phase 0 (skeleton) | A2A-01 |
| Phase 1 (core contracts + dispatcher) | A2A-02 (ports), A2A-03 (dispatcher + stores) |
| Phase 2 (defaults + spring-ai) | A2A-03 (in-memory stores), A2A-04 (spring-ai) |
| Phase 3 (SDK wire-up / validation) | A2A-07 (client + integration test) |
| Phase 4 (starter + sample apps) | A2A-05 (autoconfig), A2A-06 (HTTP), A2A-08 (sample apps) |
| Phase 5 (upstream, deferred) | NOT in v1 — documented in design §8; skip |

Rationale for ordering: the starter can be built contract-first (controllers → our
`A2ARequestDispatcher`) without the SDK `RequestHandler` bridge; the Phase 3 SDK
bridge becomes an optional migration artifact inside A2A-07, not a prerequisite.

## 3. Verified facts — CORRECTIONS to the design doc (checked against Maven Central + SDK jars, 2026-08-23)

| Design doc says | Verified truth |
|---|---|
| SDK coordinates `io.github.a2asdk:*` | **`org.a2aproject.sdk:*:1.2.0.Final`** — the `io.github.a2asdk` group is legacy (last release `1.0.0.Alpha3`). Official repo `a2aproject/a2a-java` publishes under `org.a2aproject.sdk`. The community baseline pinned `io.github.a2asdk:0.3.3.Final`. |
| package `io.a2a.spec.*` | **`org.a2aproject.sdk.spec.*`** |
| package `io.a2a.server.RequestContext` | **`org.a2aproject.sdk.server.agentexecution.RequestContext`** |
| `JSONRPCError` error type | SDK error base is **`org.a2aproject.sdk.spec.A2AError`** — `extends RuntimeException implements Event`, has `getCode()`, `getMessage()`, `getDetails()`. |
| Spring AI "0.4.0-SNAPSHOT" | Use **Spring AI 2.0.1** (`spring-ai-model`, `spring-ai-client-chat`) — built on Spring Framework 7.0.9, aligned with this repo's **Spring Boot 4.1.0**. |

### 3.1 SDK artifact coordinates (all `org.a2aproject.sdk:*:1.2.0.Final`)

| Artifact | Contents |
|---|---|
| `a2a-java-sdk-spec` | Wire model: `org.a2aproject.sdk.spec.*` |
| `a2a-java-sdk-server-common` | Server runtime: `org.a2aproject.sdk.server.*` (`RequestHandler`, `DefaultRequestHandler`, `ServerCallContext`, `RequestContext`, `TaskStore`, `InMemoryTaskStore`, auth classes) |
| `a2a-java-sdk-jsonrpc-common` | JSON-RPC envelopes: `org.a2aproject.sdk.jsonrpc.common.wrappers.*` |
| `a2a-java-sdk-client` | Client: `org.a2aproject.sdk.client.*` (`Client`, `ClientBuilder`, `ClientConfig`) |
| `a2a-java-sdk-client-transport-jsonrpc` | Client JSON-RPC transport + SSE: `org.a2aproject.sdk.client.transport.jsonrpc.*` |

### 3.2 Key SDK spec types used by our contracts (verified via `javap`)

```text
Event            (interface, marker)
EventKind        (interface, has String kind())
Message          (Record: Role role, List<Part<?>> parts, String messageId, String contextId,
                  String taskId, List<String> referenceTaskIds, Map<String,Object> metadata,
                  List<String> extensions; Message.builder())
Part / TextPart / FilePart / DataPart
Task             (Record: String id, String contextId, TaskStatus status, List<Artifact> artifacts,
                  List<Message> history, Map<String,Object> metadata; Task.builder() / Task.builder(Task))
TaskStatus       (Record: TaskState state, Message message, OffsetDateTime timestamp; TaskStatus(TaskState))
TaskState        (Enum: TASK_STATE_SUBMITTED, _WORKING, _INPUT_REQUIRED, _AUTH_REQUIRED, _COMPLETED,
                  _CANCELED, _FAILED, _REJECTED, _UNSPECIFIED; isFinal(), isInterrupted())
Artifact
AgentCard        (Record + AgentCard.builder())
MessageSendParams(Record: Message message, MessageSendConfiguration configuration,
                  Map<String,Object> metadata, String tenant)
MessageSendConfiguration (Record: acceptedOutputModes, historyLength, taskPushNotificationConfig, returnImmediately)
TaskQueryParams  (Record: String id, Integer historyLength, String tenant)
TaskIdParams     (Record: String id, String tenant)
TaskStatusUpdateEvent (Record: String taskId, TaskStatus status, String contextId,
                  Map<String,Object> metadata; isFinal(), isFinalOrInterrupted())
A2AError         (extends RuntimeException implements Event; getCode(), getMessage(), getDetails())
TaskNotFoundError, TaskNotCancelableError, InvalidParamsError, ... (spec errors)
```

### 3.3 SDK server runtime types (used only in A2A-07 / optional bridge)

```text
org.a2aproject.sdk.server.ServerCallContext(User, Map<String,Object>, Set<String>)  // getState(), getUser()
org.a2aproject.sdk.server.requesthandlers.RequestHandler / DefaultRequestHandler
org.a2aproject.sdk.server.agentexecution.RequestContext
org.a2aproject.sdk.server.auth.{User, AuthenticatedUser, UnauthenticatedUser, TaskAuthorizationProvider}
org.a2aproject.sdk.server.tasks.{TaskStore, InMemoryTaskStore, TaskManager, AgentEmitter, PushNotificationSender}
```

### 3.4 Spring AI (A2A-04)

- `org.springframework.ai:spring-ai-client-chat:2.0.1` (pulls `spring-ai-model`, Spring Framework 7.0.9, Reactor).
- `ChatClient` lives at `org.springframework.ai.chat.client.ChatClient`.
- Fluent API: `chatClient.prompt().user(String).call().content()` (blocking) and
  `chatClient.prompt().user(String).stream().content()` (returns `Flux<String>`).

## 4. Module layout — all under `ai/a2a/`, each an ISOLATED Gradle build

Each module is a self-contained composite build (own `settings.gradle` including
`build-logic`), registered in root `settings.gradle` via `includeBuild(...)`.
Base package root: **`io.github.khezyapp.a2a.*`**. Version: **`1.0.0`** (samples `0.0.1-SNAPSHOT`).

| Path | rootProject.name | Plugin | Depends on (coordinates) |
|---|---|---|---|
| `ai/a2a/a2a-core` | `a2a-core` | `khezy.java-library` | `org.a2aproject.sdk:a2a-java-sdk-spec:1.2.0.Final` (as `api`) |
| `ai/a2a/a2a-core-default` | `a2a-core-default` | `khezy.java-library` | `io.github.khezyapp:a2a-core:1.0.0` (`api`), sdk-spec, sdk-client, sdk-client-transport-jsonrpc |
| `ai/a2a/a2a-spring-ai` | `a2a-spring-ai` | `khezy.java-library` + `khezy.java-use-mockito` | `io.github.khezyapp:a2a-core:1.0.0` (`api`), `org.springframework.ai:spring-ai-client-chat:2.0.1` |
| `ai/a2a/a2a-spring-boot-starter` | `a2a-spring-boot-starter` | `khezy.springboot-library` | `a2a-core` + `a2a-core-default` (`api`), sdk-jsonrpc-common, `spring-boot-starter-webmvc`, `spring-boot-starter-security` (compileOnly + test) |
| `ai/a2a/a2a-server-sample` | `a2a-server-sample` | `khezy.springboot` | `a2a-spring-boot-starter` |
| `ai/a2a/a2a-client-sample` | `a2a-client-sample` | `khezy.springboot` | `a2a-core`, `a2a-core-default` |

Composite substitution note: modules reference each other by published coordinates
(`io.github.khezyapp:a2a-core:1.0.0`); Gradle composite builds substitute the included
build. Cross-build tasks run from root: `./gradlew :ai:a2a:a2a-core:test`.

## 5. Task graph

| # | File | Task | Prerequisite files |
|---|---|---|---|
| 1 | `01-a2a-skeleton.md` | A2A-01 Scaffold 4 library modules + register in root `settings.gradle` | `00` |
| 2 | `02-a2a-core-ports.md` | A2A-02 `a2a-core`: port interfaces, records, error taxonomy + tests | `00`, `01` |
| 3 | `03-a2a-core-default.md` | A2A-03 `a2a-core-default`: `DefaultA2ARequestDispatcher`, `InMemoryTaskStore`, `InMemoryEventQueue`, `NoopPushNotificationSender` + tests | `00`, `02` |
| 4 | `04-a2a-spring-ai.md` | A2A-04 `a2a-spring-ai`: `ChatClientExecutors` + handlers + tests | `00`, `02` |
| 5 | `05-a2a-spring-boot-starter.md` | A2A-05 starter: `A2AAutoConfiguration`, `A2AProperties`, `SecurityCallerIdentityResolver`, `AutoConfiguration.imports` + reflection tests | `00`, `02`, `03` |
| 6 | `06-starter-http-endpoints.md` | A2A-06 starter: `AgentCardController`, `MessageController`, `TaskController` + JSON-RPC mapping + WebMvcTest | `00`, `02`, `03`, `05` |
| 7 | `07-sdk-client-and-integration.md` | A2A-07 `SdkA2ARemoteAgentClient` (client port default) + end-to-end `@SpringBootTest` IT | `00`, `02`, `03`, `06` |
| 8 | `08-sample-apps.md` | A2A-08 sample apps: `a2a-server-sample` (math agent) + `a2a-client-sample` (host agent) + end-to-end validation | `00`, `02`, `03`, `06`, `07` |
| 9 | `09-readme-and-release.md` | A2A-09 READMEs (KHEZY tone), full `check` + checkstyle, graphify update, publish notes | all |

## 6. Shared conventions (bind in every task)

### 6.1 Module recipe
- `settings.gradle`: `pluginManagement { includeBuild("../../../build-logic") }` + `rootProject.name = "<name>"` (path from `ai/a2a/<module>` to repo root is `../../..`).
- `build.gradle`: apply a `khezy.*` convention plugin, `group = "io.github.khezyapp"`, `version = "1.0.0"`, `mavenPublishing { pom { name = ...; description = ... } }`.
- **Never apply `java-library` directly.**
- Register every module in root `settings.gradle` with `includeBuild("ai/a2a/<name>")`.

### 6.2 Code style (Checkstyle 13.1.0, CI-enforced on `src/main/java`)
- `final` on all method parameters and local variables; `final var x = ...`.
- 4-space indent, 120-char line limit, Egyptian braces, braces on all blocks.
- No star imports in production code; `Objects.requireNonNull` for validation.
- Checkstyle tasks: `./gradlew :ai:a2a:a2a-core:checkstyleMain` (and `checkstyleTest`).

### 6.3 Build & verify commands (run from repo root)
```bash
./gradlew :ai:a2a:<module>:test          # single module tests (composite resolves cross-build deps)
./gradlew :ai:a2a:<module>:check         # full check incl. checkstyle
./gradlew unittest                       # root aggregate: tests on ALL non-example included builds
./gradlew checkstyleMains checkstyleTests
./gradlew -p ai/a2a/<module> check       # standalone (only works if module has no cross-build deps)
./gradlew -p ai/a2a/a2a-server-sample bootRun
```

### 6.4 Spring Boot 4 / Jackson 3 notes (bind where JSON is used)
- Jackson 3: `tools.jackson.databind.ObjectMapper` (NOT `com.fasterxml.jackson.databind`). Annotations stay `com.fasterxml.jackson.annotation.*`.
- Auto-configuration registration goes ONLY in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (one FQCN per line). Never in `@Import` for conditionally-gated classes. Do NOT use `spring.factories` for auto-config.
- Web MVC tests: `spring-boot-starter-webmvc-test` (already provided by `khezy.springboot-library`). Do not use `spring-boot-starter-test`.

### 6.5 SDK API discovery (use this instead of guessing signatures)
Download once and inspect with `javap`:
```bash
curl -sL -o /tmp/a2a-spec.jar https://repo.maven.apache.org/maven2/org/a2aproject/sdk/a2a-java-sdk-spec/1.2.0.Final/a2a-java-sdk-spec-1.2.0.Final.jar
javap -classpath /tmp/a2a-spec.jar org.a2aproject.sdk.spec.<TypeName>
# repeat for a2a-java-sdk-server-common, a2a-java-sdk-client, a2a-java-sdk-client-transport-jsonrpc, a2a-java-sdk-jsonrpc-common
```

### 6.6 After every task
- Run the task's verification commands.
- **Append a handoff entry to `99-handoff-log.md`** using the template at the top of that file (see §6.7).
- Run `graphify update .` from the repo root to keep the knowledge graph current.

### 6.7 Handoff protocol (binding — context goes in files, never in chat)

**Why:** a handoff written in chat costs its tokens again on every subsequent turn of
the session (conversation history is re-sent each request) and is lost entirely when
the session ends. A handoff appended to `99-handoff-log.md` is written once and read
once by the next task's agent — the cheapest durable channel, and it survives across
sessions and context compaction.

**Rules:**

1. On FINISHING a task: append one entry to `99-handoff-log.md`, following the
   template in that file. Include real deviations (renamed types, SDK signature
   surprises, extra helpers) — the next agent relies on REAL shapes, not planned ones.
2. Do NOT paste the handoff into your final chat message. Chat reply is one line:
   "task complete, log entry written".
3. On STARTING a task: read this file + your task file + the LAST entry per
   prerequisite task in `99-handoff-log.md`. Nothing else.
4. Never edit or delete earlier log entries — append only.
5. Each task file's bottom "Handoff note for the next task" section lists WHAT to
   record; §6.7 defines WHERE: always `99-handoff-log.md`.

## 7. Guardrails / non-goals for v1

- Do NOT re-model the wire model; only `org.a2aproject.sdk.spec.*` types in port signatures.
- Do NOT contribute upstream (Phase 5) in v1.
- Do NOT add Reactor to `a2a-core`/`a2a-core-default`; `AgentEventSink` is callback-based.
- The SDK `RequestHandler` bridge is OPTIONAL (A2A-07); the final architecture routes HTTP → our `A2ARequestDispatcher` only.
- Every port bean in the starter must be `@ConditionalOnMissingBean` so users can override.

## 8. Definition of Done (applies to every task)

1. All listed files created; code compiles; checkstyle clean on `src/main/java` (and tests where run).
2. Listed tests exist and pass.
3. Task's verification commands pass from repo root.
4. `graphify update .` run.
5. Handoff entry appended to `99-handoff-log.md` per §6.7 (chat is NOT a valid handoff channel).

