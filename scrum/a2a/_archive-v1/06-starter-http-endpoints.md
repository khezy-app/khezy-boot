# A2A-06 — `a2a-spring-boot-starter`: JSON-RPC HTTP endpoints

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md`, `03-a2a-core-default.md`, `05-a2a-spring-boot-starter.md`.

**Do NOT modify** other modules. Code lives in `ai/a2a/a2a-spring-boot-starter/src/main/java` (package `io.github.khezyapp.a2a.starter.web.*`).

---

## 1. Goal

Expose the A2A protocol over HTTP: agent card discovery + JSON-RPC `message/send`,
`message/stream`, `tasks/get`, `tasks/cancel`. **Controllers call only
`A2ARequestDispatcher`** (never SDK server runtime classes), per design §6.

## 2. Context digest

- Design §6 + §8 Phase 4; community controller surface: `AgentCardController`
  (`/.well-known/agent-card.json`), `MessageController` (`message/send`, `message/stream`),
  `TaskController` (`tasks/get`, `tasks/cancel`).
- SDK envelope types in `org.a2aproject.sdk.jsonrpc.common.wrappers.*` (dependency already
  declared in A2A-01): `A2ARequest`, `A2AResponse`, `SendMessageRequest`, `SendMessageResponse`,
  `GetTaskRequest`, `GetTaskResponse`, `CancelTaskRequest`(name to verify). Inspect with
  `javap` (see `00` §6.5) — they carry the JSON-RPC `method`, `params`, `id`, `result`/`error`.
- Jackson 3 (`tools.jackson.databind.ObjectMapper`) is Spring Boot 4's serializer; let
  Spring inject the auto-configured `ObjectMapper` and use it for request/response bodies.
- Caller identity: build `CallContext` from the incoming `HttpServletRequest` +
  `CallerIdentityResolver.resolve()` (bean from A2A-05).
- `message/stream` → SSE response (`text/event-stream`); keep a simple
  `SseEmitter`/`StreamingResponseBody` approach (non-reactive; `AgentEventSink` is callback-based).
- `A2AWebAutoConfiguration` must be registered in `AutoConfiguration.imports` (append to the file from A2A-05).

## 3. Classes to create (package `io.github.khezyapp.a2a.starter.web`)

### 3.1 `HttpCallContext implements CallContext`

- Constructor `(CallerIdentity caller, Map<String, Object> attributes)`; expose both accessors; immutable.
- Controller helper: `HttpCallContext.from(HttpServletRequest request, CallerIdentityResolver resolver)` — `attributes` seeded with `request.getRequestURI()`, `request.getMethod()`, and (if present) `request.getRemoteAddr()`.

### 3.2 `AgentCardController`

- `@RestController`, route `GET /.well-known/agent-card.json`.
- Body: `dispatcher.agentCard(ctx)` serialized as JSON (ObjectMapper writes the `AgentCard` record directly).

### 3.3 `MessageController`

- `@RestController`, `@RequestMapping("/message")`.
- `POST /message` handling JSON-RPC methods:
  - `message/send` → parse body into `SendMessageRequest` (or `A2ARequest`) → build `MessageSendParams` → `dispatcher.onMessageSend(params, ctx)` → wrap result as `SendMessageResponse`-shaped JSON with the same JSON-RPC `id`.
  - `message/stream` → SSE: create a callback `AgentEventSink` that writes each `chunk`/`statusChanged`/`completed` as an SSE event, and `dispatcher.onMessageStream(params, sink, ctx)` (dispatch async if you keep the servlet thread free; a synchronous write into `SseEmitter` is acceptable for v1).
- Map dispatcher exceptions to JSON-RPC error envelopes (`A2AError` → code/message/details; `TaskNotFoundException` → spec `TaskNotFoundError` shape; `TaskNotCancelableException` → `TaskNotCancelableError` shape). Verify the SDK's error records (`org.a2aproject.sdk.spec.*Error`) via `javap` and reuse their JSON shape.

### 3.4 `TaskController`

- `@RestController`, `@RequestMapping("/tasks")`.
- `POST /tasks/get` → `GetTaskRequest` → `TaskQueryParams(query.id(), query.historyLength(), query.tenant())` (adjust to the verified wrapper components) → `dispatcher.onGetTask(...)` → `GetTaskResponse`-shaped JSON.
- `POST /tasks/cancel` → `CancelTaskRequest` → `TaskIdParams(id, tenant)` → `dispatcher.onCancelTask(...)` → task JSON.

### 3.5 `A2AWebAutoConfiguration`

- `@AutoConfiguration` + `@ConditionalOnClass({A2ARequestDispatcher.class, ObjectMapper.class})` + `@ConditionalOnWebApplication(type = SERVLET)` + `@ConditionalOnBean(A2ARequestDispatcher.class)`.
- `@Bean` methods for the three controllers (constructor-inject the dispatcher + resolver + ObjectMapper).
- Append `io.github.khezyapp.a2a.starter.web.A2AWebAutoConfiguration` to `AutoConfiguration.imports`.

## 4. Tests (`src/test/java`) — `@WebMvcTest` via `spring-boot-starter-webmvc-test`

Per AGENTS.md, use `spring-boot-starter-webmvc-test` (not `spring-boot-starter-test`).
- `@WebMvcTest(controllers = {AgentCardController.class, MessageController.class, TaskController.class})` with `@MockBean A2ARequestDispatcher` + a real `SecurityCallerIdentityResolver` (or a stub resolver bean).
- `AgentCardControllerTest` — GET `/.well-known/agent-card.json` returns the stubbed card as JSON.
- `MessageControllerTest` — POST JSON-RPC `message/send` body → 200, response contains the expected `result`/`id`; stub `onMessageSend` returns a `TaskStatusUpdateEvent`.
- `MessageControllerStreamTest` — POST `message/stream` → `text/event-stream`, emits at least one SSE data frame.
- `TaskControllerTest` — `tasks/get` and `tasks/cancel` round-trip with stubbed dispatcher; unknown task → proper JSON-RPC error envelope.
- Keep the tests focused on envelope mapping — do NOT load a full application context.

## 5. Verification

```bash
./gradlew :ai:a2a:a2a-spring-boot-starter:test
./gradlew :ai:a2a:a2a-spring-boot-starter:checkstyleMain :ai:a2a:a2a-spring-boot-starter:checkstyleTest
./gradlew :ai:a2a:a2a-spring-boot-starter:check
```

Manual smoke (optional, needs a dispatcher bean — otherwise use the sample app in A2A-08):
```bash
curl -s http://localhost:8080/.well-known/agent-card.json
```

## 6. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Document the exact wrapper types used (`SendMessageRequest`, `GetTaskRequest`, etc.),
their component names, and the SSE approach chosen. A2A-07 builds an end-to-end test on
top of these endpoints.
Then run `graphify update .`.

