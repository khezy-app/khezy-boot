## Project

**khezy-boot** — Spring Boot starters and auto-configurations for the KHEZY ecosystem. Simplifies exception handling, request auditing, security, and dynamic query patterns for Khmer developers. Published to Maven Central as `io.github.khezyapp:*`.

See `about/khezy-mission-vision.md` for brand identity and guiding philosophy.

## Architecture

Gradle **composite build** — each subdirectory is an independent Gradle build with its own `settings.gradle`. Root `settings.gradle` uses `includeBuild(...)` (never `include`).

| Group | Modules | Plugin |
|---|---|---|
| `rest-api/` | `api-audit`, `api-exception`, `api-security` | `khezy.springboot-library` |
| `auto-configurations/rest-api/` | `api-exception-spring-boot-starter`, `api-security-spring-boot-starter` | `khezy.springboot-library` |
| `db-query/` | `query-grammar` (ANTLR), `query-jpa-spec`, `query-jooq-spec` | `khezy.java-library` |
| `examples/` | `db-query-jpa-spec`, `db-query-jooq-spec` | `khezy.springboot` |
| `build-logic/` | Convention plugins (`khezy.*`) | Separate included build |

## Build & Verify

```bash
# Run tests across all modules
./gradlew check

# Checkstyle only (all modules except examples and build-logic)
./gradlew checkstyleMains checkstyleTests

# Single module test (run from root — composite build resolves cross-build deps)
./gradlew :rest-api:api-exception:test

# Single module checkstyle
./gradlew :rest-api:api-exception:checkstyleMain
```

No dedicated lint/typecheck commands exist — Checkstyle is the static analysis gate (13.1.0, rules in `build-logic/src/main/resources/config/checkstyle/checkstyle.xml`).

## Convention Plugins

Located in `build-logic/src/main/groovy/`. Key ones:

- **`khezy.java-library`** — base + JUnit 5 + Maven Publish + Checkstyle. Use for pure Java modules.
- **`khezy.springboot-library`** — base + Spring dependency management + Maven Publish + Checkstyle. Use for Spring Boot starter libraries (no bootJar).
- **`khezy.springboot`** — like `springboot-library` but with `bootJar` enabled. Use for runnable Spring Boot apps (examples).
- **`khezy.java-use-mockito`** — adds Mockito with agent arg; apply only when tests need mocking.

**Never apply `java-library` directly** — always use a `khezy.*` convention plugin.

## Code Style (agent-critical)

- Java 17 target, JDK 21 toolchain, UTF-8
- `final` on **all** method parameters and local variables: `final var x = ...`
- `var` universal for locals, always prefixed with `final`
- 4-space indent, 120-char line limit, Egyptian braces, braces on all blocks
- No star imports in production code; `Objects.requireNonNull` for validation
- Checkstyle enforced on `src/main/java` — will fail CI on violations

## Module Recipe

New module `settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../build-logic")
}
rootProject.name = "my-module"
```

New module `build.gradle`:
```groovy
plugins {
    id("khezy.springboot-library")  // or khezy.java-library
}
group = "io.github.khezyapp"
version = "1.0.0"
```

## Publishing

Manual GitHub Actions workflow (`manual_release.yml`). Publishes via `com.vanniktech.maven.publish` to Maven Central. Requires `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `GPG_KEY_ID`, `GPG_ARMOR_KEY`, `GPG_PASSWORD` secrets.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
