# AI Elements Sample (servlet app)

A runnable Spring Boot **servlet** app that exposes `POST /api/chat` and returns the
ai-elements **UI-message-stream** SSE bytes. It pairs both library modules together:
`ai-elements-model` (wire events) + `ai-elements-spring-ai` (Spring AI conversion and
SSE writing), and is the parity checkpoint for the plan — the bytes must match the
reference stream the frontend expects.

## Two modes: mock-first, then live DeepSeek

The app is **mock-first**: by default (`mock` profile) it replays a canned `SseEvent`
sequence so you can verify the wire format byte-for-byte **without a model API key**.
Switch to the `deepseek` profile to prove the real Spring AI → ai-elements pipeline end
to end using DeepSeek.

| Profile | Behavior | Needs a key? |
|---|---|---|
| `mock` (default) | Replays `mock/complex-workflow-stream.jsonl` as `start`, `start-step`, `reasoning-*`, `tool-input-start`, `tool-output-available`, `text-*`, `finish-step`, `finish`, `[DONE]` | No |
| `deepseek` | `ChatClient` (DeepSeek) → `.stream().chatResponse()` → live `text` deltas (event id `"0"`) | Yes (`DEEPSEEK_API_KEY`) |

In `deepseek` mode the Spring AI `DeepSeekChatAutoConfiguration` supplies the chat
model; in `mock` mode it is explicitly excluded
(`application-mock.yaml`) so no key is required at startup.

## Run it

```bash
./gradlew -p ai/ai-elements/samples/ai-elements-sample bootRun            # mock (default)
./gradlew -p ai/ai-elements/samples/ai-elements-sample bootRun \
  --args='--spring.profiles.active=deepseek'                              # live DeepSeek
```

For live DeepSeek, export the key first:

```bash
export DEEPSEEK_API_KEY=sk-...
```

## Verify with curl

```bash
curl -sN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  -d '{"id":"chat_1","messages":[{"id":"m1","role":"user","content":"hello",
       "parts":[{"type":"text","text":"hello"}]}],
       "trigger":"submit-message","messageId":null}'
```

Assert:

1. Response headers: `Content-Type: text/event-stream`, `Cache-Control: no-cache`,
   `X-Vercel-AI-UI-Message-Stream: v1`.
2. Stream lines match the reference event set — `data: {...}` per event, ending with a
   `data: [DONE]` terminal line.
3. Under `deepseek` the same envelope arrives with live `text-delta` events.

## Test

A single `@SpringBootTest` + `MockMvc` smoke test
(`AiElementsSampleSmokeTest`) asserts the mock stream returns `200` +
`text/event-stream` + a `[DONE]` terminal line:

```bash
./gradlew -p ai/ai-elements/samples/ai-elements-sample test
```

## What this does NOT do

- **No provider orchestration.** The sample wires DeepSeek as an example; tool-output
  orchestration is the app's job (this demo emits the reference event shape only).
- **No starter / autoconfiguration.** The endpoint is a plain controller, mirroring
  exactly how you would add it to your own app until a starter exists.
- **Frontends are out of scope** — this app only produces the SSE stream.
