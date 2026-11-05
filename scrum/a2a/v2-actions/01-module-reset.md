# A2A2-01 — Module reset, SDK upgrade, store module

**Prerequisites:** `00-plan-overview.md`.
**Design:** §4, §10.

## 1. Goal

Delete the v1 reimplementation, upgrade the SDK baseline, add `a2a-store-jpa`, and leave
all modules compiling with placeholder-free contents. No new behavior in this task.

## 2. Work

### 2.1 Delete v1 reimplementation

Execute the cleanup manifest in `00` §5. Concretely:

```
# a2a-core
rm -rf ai/a2a/a2a-core/src/main/java/io/github/khezyapp/a2a/core/{client,dispatch,error,identity,store}
rm -rf ai/a2a/a2a-core/src/main/java/io/github/khezyapp/a2a/core/executor
rm -f  ai/a2a/a2a-core/src/test/java/io/github/khezyapp/a2a/core/{AbstractA2AAgentExecutorTest,ErrorTaxonomyTest}.java

# a2a-core-default
rm -rf ai/a2a/a2a-core-default/src/main/java/io/github/khezyapp/a2a/coredef/{dispatch,executor,store,client}
rm -rf ai/a2a/a2a-core-default/src/test/java/io/github/khezyapp/a2a/coredef/{dispatch,store,client}

# a2a-spring-ai
rm -f ai/a2a/a2a-spring-ai/src/main/java/io/github/khezyapp/a2a/springai/{ChatClientExecutors,ChatClientMessageHandler,StreamingChatClientMessageHandler}.java
rm -f ai/a2a/a2a-spring-ai/src/test/java/io/github/khezyapp/a2a/springai/ChatClientExecutorsTest.java

# starter
rm -rf ai/a2a/a2a-spring-boot-starter/src/main/java/io/github/khezyapp/a2a/starter/{security,web}
rm -f  ai/a2a/a2a-spring-boot-starter/src/main/java/io/github/khezyapp/a2a/starter/{A2AAutoConfiguration,A2AClientAutoConfiguration}.java
rm -f  ai/a2a/a2a-spring-boot-starter/src/test/java/io/github/khezyapp/a2a/starter/{A2AAutoConfigurationTest,A2AClientAutoConfigurationTest,A2AClientServerIntegrationTest,AutoConfigurationImportsTest}.java
rm -rf ai/a2a/a2a-spring-boot-starter/src/test/java/io/github/khezyapp/a2a/starter/{security,web}
```

Keep `A2AProperties.java` for now (rewritten in task 06). Keep `TestBootConfiguration`
only if still used after task 06; otherwise delete there.

Delete stale build output: `ai/a2a/*/build/` and generated `.class` files are ignored by
git; no action needed beyond a clean build.

### 2.2 Upgrade SDK coordinates

Set `a2aSdkVersion = "1.4.0.Final"` in every `build.gradle` (or import
`platform("org.a2aproject.sdk:a2a-java-sdk-bom:1.4.0.Final")` and drop per-artifact
versions), and align artifact sets:

| Module | Dependencies |
|---|---|
| `a2a-core` | `api "org.a2aproject.sdk:a2a-java-sdk-server-common:${a2aSdkVersion}"`, `api "org.a2aproject.sdk:a2a-java-sdk-client:${a2aSdkVersion}"` |
| `a2a-core-default` | `api "io.github.khezyapp:a2a-core:2.0.0"`, `implementation "org.a2aproject.sdk:a2a-java-sdk-client-transport-jsonrpc"`, `implementation "...client-transport-rest"` |
| `a2a-store-jpa` | `api "io.github.khezyapp:a2a-core:2.0.0"`, `api "org.springframework.boot:spring-boot-starter-data-jpa"` |
| `a2a-spring-ai` | `api "io.github.khezyapp:a2a-core:2.0.0"`, `implementation "org.springframework.ai:spring-ai-client-chat:2.0.1"` |
| `a2a-spring-boot-starter` | `api "io.github.khezyapp:a2a-core-default:2.0.0"`, `implementation "...a2a-java-sdk-transport-jsonrpc"`, `implementation "...a2a-java-sdk-spec-grpc"`, webmvc; `compileOnly` security + jpa + spring-ai |

Version bumps: all library modules `2.0.0` (breaking major). Samples stay
`0.0.1-SNAPSHOT`.

### 2.3 Create `ai/a2a/a2a-store-jpa`

- `settings.gradle`:
  ```groovy
  pluginManagement { includeBuild("../../build-logic") }
  rootProject.name = "a2a-store-jpa"
  ```
- `build.gradle`: `id("khezy.java-library")`, group/version, `mavenPublishing`.
- Register in root `settings.gradle`: `includeBuild("ai/a2a/a2a-store-jpa")`.
- Create package dirs `io/github/khezyapp/a2a/store/jpa/{task,push,scope,crypto}`.
  No classes yet — task 05 fills them.

### 2.4 Root registration and docs

- Ensure root `settings.gradle` still includes all four existing modules + samples +
  the new store module.
- Update `ai/a2a/README.md` to a short "work in progress — see scrum/a2a/a2a-design-v2.md"
  placeholder; the full README is task 08.
- Add `scrum/a2a/README.md` index:
  - design link,
  - action plan link,
  - handoff log link.

## 3. Verification

```bash
./gradlew :a2a-core:compileJava :a2a-core-default:compileJava :a2a-spring-ai:compileJava
./gradlew :a2a-store-jpa:compileJava
```

All compile (empty modules compile trivially). No v1 class remains referenced.

## 4. Handoff

Record: exact SDK version resolved, modules registered, any dependency conflict
(especially Spring Data JPA / Boot 4 package names). Run `graphify update .`.
