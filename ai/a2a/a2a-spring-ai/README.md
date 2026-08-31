# Khezy A2A Spring AI

Optional adapter that turns a Spring AI `ChatClient` into an A2A agent executor. It
sits on top of Spring AI — you build and configure the `ChatClient` (model, system
prompt, advisors); this module only bridges it into the
[`a2a-core`](../a2a-core/) executor ports.

**Prerequisite:** this module assumes you already know Spring AI's `ChatClient`
(`spring-ai-client-chat:2.0.1`). If prompts, models, and advisors are new to you, start
with the [Spring AI reference](https://docs.spring.io/spring-ai/reference/) first.

`io.github.khezyapp:a2a-spring-ai:1.0.0`

## Usage

```java
@Bean
AbstractA2AAgentExecutor myAgent(final ChatClient chatClient) {
    return ChatClientExecutors.from(
            chatClient,
            // blocking: receives the incoming Message, returns the reply text
            (message, context) -> "you said: " + message.parts().get(0),
            // streaming override: push chunks through the sink yourself
            (message, context, sink) -> {
                sink.chunk(new TextPart("partial"));
                sink.completed(...);
            });
}
```

- `ChatClientExecutors.from(chatClient, blockingHandler)` → blocking-only; streaming is
  served by the base class fallback (single completed event).
- `ChatClientExecutors.from(chatClient, blockingHandler, streamingHandler)` → both paths;
  note the streaming handler delegates **entirely to your code**.
- `ChatClientExecutors.reactiveStreamingHandler()` → built-in handler that consumes a
  `Flux<String>` of chunks and completes with the accumulated text — pair it with
  `chatClient.prompt().user(text).stream().content()`.

Handler interfaces: `ChatClientMessageHandler`
`(ChatClient, AgentExecutionContext) -> String` and
`StreamingChatClientMessageHandler` `(ChatClient, AgentExecutionContext, AgentEventSink)`.

## What this does NOT do

- No model wiring — you own `ChatClient` construction (OpenAI, Ollama, …).
- No prompt templates or conversation memory — bring your own.
- No auto-configuration — plain factory methods; wire the returned executor as a bean
  yourself (or via the [starter](../a2a-spring-boot-starter/)).
