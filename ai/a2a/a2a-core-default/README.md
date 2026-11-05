# Khezy A2A Core Default

Ready-to-use client implementations for [`a2a-core`](../a2a-core/) on the official A2A
Java SDK. Each class is a replaceable bean when you use the starter.

`io.github.khezyapp:a2a-core-default:1.0.0` · package root
`io.github.khezyapp.a2a.coredef.client`.

## What's inside

| Class | Behavior |
|---|---|
| `card.SdkAgentCardSource` | Fetches cards with the SDK URL rules (well-known path, tenant/custom path, 404 fallback), conditional `GET`, and proto-JSON parsing. |
| `card.CachingAgentCardResolver` | In-memory card cache (5 min default), conditional revalidation, stale-if-error, `invalidate`/`clear`. |
| `A2AClientFactory` | Builds SDK clients from a card: output modes, streaming only when advertised, every discovered transport, optional auth interceptor. |
| `RemoteAgentRegistry` | Fail-soft URL registration, lookup by name/URL/skill, `refresh`, `unregister`, owns and closes SDK clients. |
| `invoke.SdkA2AInvoker` | Builds SDK messages/params, sends, and normalizes the first settled event into `SendOutcome`; never closes the shared client. |
| `store.InMemoryA2AConversationStore` | TTL + LRU conversation memory keyed by `agent|skill`. |
| `transport.A2AClientTransports` | Discovers JSON-RPC and REST transports, plus `ServiceLoader` additions. |

## Usage

```java
final var source = new SdkAgentCardSource(httpClient);
final var resolver = new CachingAgentCardResolver(source);
final var registry = new RemoteAgentRegistry(resolver, new A2AClientFactory(),
        ClientBuildOptions.of(httpClient), List.of("http://localhost:8080/a2a"));
final var connector = new A2AConnector(new SdkA2AInvoker(),
        new InMemoryA2AConversationStore());
```

In Spring Boot, every one of these is auto-configured by the starter.

## Gotchas

- Card parsing needs `a2a-java-sdk-spec-grpc`; plain Jackson/Gson does **not** produce a
  valid card.
- `RemoteAgentRegistry` is keyed by base URL; duplicate agent names are possible and
  `find` returns the first match.
- `SdkA2AInvoker` does not cancel the remote task on a local timeout — it stops waiting.
- A non-streaming client on a streaming card returns the last observed pending outcome if
  the SDK call returns before a terminal event.

## What this does NOT do

- No durable conversation storage — the default store is in memory only.
- No token issuance/refresh; supply SDK `CredentialService`/interceptors.
- No server transport — HTTP endpoints belong to the
  [starter](../a2a-spring-boot-starter/).
