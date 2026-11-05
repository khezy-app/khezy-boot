# Khezy A2A Observability OpenTelemetry

Optional OpenTelemetry tracing for the A2A starter. It wires the official SDK
OpenTelemetry extras without coupling `a2a-core` or the starter to OpenTelemetry.

`io.github.khezyapp:a2a-observability-otel:1.0.0` · package root
`io.github.khezyapp.a2a.observability.otel`.

## Activation

Add this module plus the SDK extras and an OpenTelemetry SDK (the extras are optional /
provided so the starter never pulls them in):

```groovy
implementation "io.github.khezyapp:a2a-observability-otel:1.0.0"
implementation "org.a2aproject.sdk:a2a-java-sdk-opentelemetry-server:1.4.0.Final"
implementation "org.a2aproject.sdk:a2a-java-sdk-opentelemetry-client:1.4.0.Final"
implementation "org.a2aproject.sdk:a2a-java-sdk-opentelemetry-client-propagation:1.4.0.Final"
implementation "io.opentelemetry:opentelemetry-sdk"
```

The auto-configuration is active when an `io.opentelemetry.api.trace.Tracer` bean exists.
Register the OpenTelemetry SDK through the Spring Boot OpenTelemetry starter or your own
beans.

## What it wires

| Side | Bean / parameter | Effect |
|---|---|---|
| Server | `a2aOpenTelemetryRequestHandler` (`@Primary RequestHandler`) | Wraps the starter's `DefaultRequestHandler` in the SDK decorator; one server span per JSON-RPC method. |
| Client | `a2aOpenTelemetryClientBuildOptionsPostProcessor` | Adds `OTEL_TRACER_KEY` and `OTEL_OPEN_TELEMETRY_KEY` to `ClientBuildOptions.transportParameters`; the SDK `ServiceLoader` wrappers then trace calls and inject W3C trace headers. |

The server bean is `@Primary`, so the starter's JSON-RPC handler receives the traced
handler while the original `DefaultRequestHandler` bean (and any `A2AServerCustomizer`)
stays available. The client post-processor keeps the starter's property binding,
interceptors and credential service untouched.

SDK wrapper keys:

- `org.a2aproject.sdk.extras.opentelemetry.Tracer`
- `org.a2aproject.sdk.extras.opentelemetry.OpenTelemetry`

## Properties (`io.github.khezyapp.a2a.observability.otel`)

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. |
| `capture-request` | `false` | Sets the SDK `org.a2aproject.sdk.server.extract.request` system property (payloads in spans). |
| `capture-response` | `false` | Sets the SDK `org.a2aproject.sdk.server.extract.response` system property. |

Payload capture is **off by default** because the SDK decorator writes message content
into span attributes. Enabling it is a JVM-wide switch (that is how the SDK reads it).

## What this does NOT do

- It does not configure exporters, sampling or resource attributes — use your
  OpenTelemetry SDK / Spring Boot OpenTelemetry configuration.
- It does not trace agent business logic; add your own spans inside `AgentExecutor`.
- It does not propagate baggage or vendor-specific headers; only the SDK wrappers'
  default W3C trace-context propagation.
- It does not make OpenTelemetry a transitive dependency of the starter.
