# A2A-08 — Sample apps: `a2a-server-sample` (math agent) + `a2a-client-sample` (host agent)

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md`, `03-a2a-core-default.md`, `06-starter-http-endpoints.md`, `07-sdk-client-and-integration.md`.

**Do NOT modify** library modules. Sample apps are throwaway validation projects (design
"Phase 4 — local validation (our own apps first)"); they live under `ai/a2a/` and are
isolated builds registered in root `settings.gradle`.

---

## 1. Goal

Prove the design in daily use: a **server** that exposes a math agent over the A2A
protocol, and a **client** host agent that discovers the server and calls it end-to-end
(host → remote math agent), mirroring the design's Phase 4 validation loop.

## 2. Context digest

- Sample app recipe (AGENTS.md "Example (self-contained) module commands"): apps apply
  `khezy.springboot` (bootJar) and their own `settings.gradle` must `includeBuild` the
  library builds they depend on (relative path from `ai/a2a/<app>` to repo root is `../../..`).
- `khezy.springboot` already brings `spring-boot-starter-webmvc`, Lombok, webmvc-test, junit5.
- The server app depends on the starter (endpoints come for free via auto-configuration).
- The client app depends on `a2a-core` + `a2a-core-default` (uses `SdkA2ARemoteAgentClient` from A2A-07).
- Root `settings.gradle`: append `includeBuild("ai/a2a/a2a-server-sample")` and `includeBuild("ai/a2a/a2a-client-sample")`.
- Root aggregate tasks (`unittest`, `checkstyleMains`) pick up these apps too (they are not under `/examples/`) — keep app code checkstyle-clean.

## 3. `ai/a2a/a2a-server-sample` — math agent

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
includeBuild("../../../ai/a2a/a2a-core")
includeBuild("../../../ai/a2a/a2a-core-default")
includeBuild("../../../ai/a2a/a2a-spring-boot-starter")
rootProject.name = "a2a-server-sample"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.springboot")
}

group = "io.github.khezyapp"
version = "0.0.1-SNAPSHOT"
description = "A2A math agent server validating the KHEZY A2A starter"

dependencies {
    implementation "${group}:a2a-spring-boot-starter"
}
```

`src/main/java/.../MathAgentApplication.java`:
```java
package io.github.khezyapp.a2a.sampleserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MathAgentApplication {

    public static void main(final String[] args) {
        SpringApplication.run(MathAgentApplication.class, args);
    }
}
```

`src/main/java/.../MathAgentExecutor.java` — a `@Component` extending
`AbstractA2AAgentExecutor`:
- `execute(AgentExecutionContext context)` parses a simple arithmetic expression
  (e.g. `"2 + 3"` → `5`) from the incoming text part, builds a reply `Message`
  (`ROLE_ASSISTANT` + `TextPart` — shapes from A2A-04's handoff), and returns a
  `TaskStatusUpdateEvent` with `TASK_STATE_COMPLETED` and the reply message.
- Keep parsing tiny (`String.split` + `Integer.parseInt`, no expression library).

`src/main/resources/application.properties`:
```properties
spring.application.name=a2a-server-sample
io.github.khezyapp.a2a.agent-card-name=Math Agent
io.github.khezyapp.a2a.agent-card-description=Solves simple arithmetic via A2A
io.github.khezyapp.a2a.agent-card-version=0.0.1-SNAPSHOT
```
(Property keys must match `A2AProperties` from A2A-05 — note relaxed binding: `agent-card-name` binds to `agentCardName`.)

## 4. `ai/a2a/a2a-client-sample` — host agent

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
includeBuild("../../../ai/a2a/a2a-core")
includeBuild("../../../ai/a2a/a2a-core-default")
rootProject.name = "a2a-client-sample"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.springboot")
}

group = "io.github.khezyapp"
version = "0.0.1-SNAPSHOT"
description = "A2A host agent validating the KHEZY A2A client port"

dependencies {
    implementation "${group}:a2a-core"
    implementation "${group}:a2a-core-default"
}
```

`src/main/java/.../HostAgentApplication.java`:
```java
package io.github.khezyapp.a2a.sampleclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HostAgentApplication {

    public static void main(final String[] args) {
        SpringApplication.run(HostAgentApplication.class, args);
    }
}
```

`src/main/java/.../HostAgentRunner.java` — a `@Component implements CommandLineRunner`:
- Property `a2a.server.url` (default `http://localhost:8080`).
- Builds `SdkA2ARemoteAgentClient.forJsonRpc(URI.create(serverUrl), Duration.ofSeconds(10))`.
- `discover(serverUrl)` → logs the agent card name.
- Builds a user `Message` (`ROLE_USER` + `TextPart("2 + 3")`), `sendMessage(...)`, logs the reply.
- `close()` the client. Wrap in try/catch with a clear log; do NOT crash on failure — exit code 0 is fine for a sample (or `System.exit(1)` on error — your choice, keep it simple and obvious in logs).

`src/main/resources/application.properties`:
```properties
spring.application.name=a2a-client-sample
a2a.server.url=http://localhost:8080
spring.main.web-application-type=none
```
(`web-application-type=none` keeps the client from starting a web server.)

## 5. Root registration — edit root `settings.gradle`

Append:
```groovy
includeBuild("ai/a2a/a2a-server-sample")
includeBuild("ai/a2a/a2a-client-sample")
```

## 6. Verification

Build everything:
```bash
./gradlew :ai:a2a:a2a-server-sample:build :ai:a2a:a2a-client-sample:build
./gradlew :ai:a2a:a2a-server-sample:checkstyleMain :ai:a2a:a2a-client-sample:checkstyleMain
```

End-to-end manual validation (host → remote math agent):
```bash
# Terminal 1: start the server
./gradlew -p ai/a2a/a2a-server-sample bootRun

# Terminal 2: agent card discovery
curl -s http://localhost:8080/.well-known/agent-card.json

# Terminal 2: raw JSON-RPC message/send
curl -s -X POST http://localhost:8080/message -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","parts":[{"text":"2 + 3"}]}}}'

# Terminal 2: run the host agent client against it
./gradlew -p ai/a2a/a2a-client-sample bootRun
```
Expected: client logs the discovered card name ("Math Agent") and the reply containing `5`.

## 7. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Note the exact JSON payload shape that worked for `message/send` (message/part field
names) — useful for the README examples in A2A-09. Confirm both apps pass checkstyle.
Then run `graphify update .`.

