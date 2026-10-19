# A2A-01 — Scaffold the A2A library modules under `ai/a2a/`

**Prerequisite task files:** `00-plan-overview.md` (this task needs no other task files).

**Do NOT modify:** any existing module outside `ai/a2a/` — except the root `settings.gradle` (registration only).

---

## 1. Goal

Create the four library module skeletons as **isolated Gradle composite builds** under
`ai/a2a/` and register them in root `settings.gradle`. Each module must compile with a
placeholder class so the composite wiring is proven before any real code lands.

## 2. Context digest

- Repo uses a Gradle **composite build**: every module is its own build with its own `settings.gradle`; the root `settings.gradle` only `includeBuild(...)`s them.
- Convention plugins live in `build-logic` (included build already wired from root). Each module's `settings.gradle` must `pluginManagement { includeBuild("../../../build-logic") }` (relative path from `ai/a2a/<module>` to repo root).
- Plugin choice (see `00` §4): `a2a-core` / `a2a-core-default` → `khezy.java-library`; `a2a-spring-ai` → `khezy.java-library` (+ `khezy.java-use-mockito` added later in A2A-04); `a2a-spring-boot-starter` → `khezy.springboot-library`.
- Modules reference each other by published coordinates (`io.github.khezyapp:a2a-core:1.0.0`); composite substitution resolves them across builds.
- Root aggregate tasks `unittest`, `checkstyleMains`, `checkstyleTests` automatically pick up any included build NOT under `/examples/` — our `ai/` modules will participate (they must pass checkstyle).

## 3. Files to create

### 3.1 `ai/a2a/a2a-core/`

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "a2a-core"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.java-library")
}

group = "io.github.khezyapp"
version = "1.0.0"

ext {
    a2aSdkVersion = "1.2.0.Final"
}

dependencies {
    api "org.a2aproject.sdk:a2a-java-sdk-spec:${a2aSdkVersion}"
}

mavenPublishing {
    pom {
        name = "Khezy A2A Core"
        description = """
        Port interfaces for the A2A contract layer: execution, dispatch, persistence,
        client calls and identity, typed on the official A2A Java SDK spec model."""
    }
}
```

Placeholder (so the module compiles and produces a jar):
`src/main/java/io/github/khezyapp/a2a/core/A2ACoreMarker.java`
```java
package io.github.khezyapp.a2a.core;

/**
 * Marker class for the {@code a2a-core} module. Real contracts arrive in A2A-02.
 */
public final class A2ACoreMarker {

    private A2ACoreMarker() {
    }
}
```

### 3.2 `ai/a2a/a2a-core-default/`

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "a2a-core-default"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.java-library")
}

group = "io.github.khezyapp"
version = "1.0.0"

ext {
    a2aSdkVersion = "1.2.0.Final"
}

dependencies {
    api "io.github.khezyapp:a2a-core:1.0.0"
    implementation "org.a2aproject.sdk:a2a-java-sdk-spec:${a2aSdkVersion}"
}

mavenPublishing {
    pom {
        name = "Khezy A2A Core Default"
        description = """
        Default implementations of the A2A core ports: in-memory task store and event
        queue, a task lifecycle dispatcher, and the SDK-client-based remote agent client."""
    }
}
```

Placeholder: `src/main/java/io/github/khezyapp/a2a/core/default/DefaultModuleMarker.java`
```java
package io.github.khezyapp.a2a.core.default;

/**
 * Marker class for the {@code a2a-core-default} module. Real defaults arrive in A2A-03.
 */
public final class DefaultModuleMarker {

    private DefaultModuleMarker() {
    }
}
```

### 3.3 `ai/a2a/a2a-spring-ai/`

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "a2a-spring-ai"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.java-library")
}

group = "io.github.khezyapp"
version = "1.0.0"

dependencies {
    api "io.github.khezyapp:a2a-core:1.0.0"
    implementation "org.springframework.ai:spring-ai-client-chat:2.0.1"
}

mavenPublishing {
    pom {
        name = "Khezy A2A Spring AI"
        description = """
        Optional Spring AI integration for the A2A contract layer: adapts a ChatClient
        into an A2A agent executor."""
    }
}
```

Placeholder: `src/main/java/io/github/khezyapp/a2a/springai/SpringAiMarker.java`
```java
package io.github.khezyapp.a2a.springai;

/**
 * Marker class for the {@code a2a-spring-ai} module. Real adapters arrive in A2A-04.
 */
public final class SpringAiMarker {

    private SpringAiMarker() {
    }
}
```

### 3.4 `ai/a2a/a2a-spring-boot-starter/`

`settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")
}
rootProject.name = "a2a-spring-boot-starter"
```

`build.gradle`:
```groovy
plugins {
    id("khezy.springboot-library")
}

group = "io.github.khezyapp"
version = "1.0.0"

ext {
    a2aSdkVersion = "1.2.0.Final"
}

dependencies {
    api "io.github.khezyapp:a2a-core:1.0.0"
    api "io.github.khezyapp:a2a-core-default:1.0.0"

    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation "org.a2aproject.sdk:a2a-java-sdk-jsonrpc-common:${a2aSdkVersion}"

    compileOnly 'org.springframework.boot:spring-boot-starter-security'
    testImplementation 'org.springframework.boot:spring-boot-starter-security'
}

mavenPublishing {
    pom {
        name = "Khezy A2A Spring Boot Starter"
        description = """
        Spring Boot starter for the A2A contract layer: auto-configuration, JSON-RPC
        HTTP endpoints and caller-identity propagation over Spring Security."""
    }
}
```

Placeholder: `src/main/java/io/github/khezyapp/a2a/starter/StarterMarker.java`
```java
package io.github.khezyapp.a2a.starter;

/**
 * Marker class for the {@code a2a-spring-boot-starter} module. Real wiring arrives in A2A-05/06.
 */
public final class StarterMarker {

    private StarterMarker() {
    }
}
```

> Note: do NOT create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` yet — it arrives in A2A-05.

### 3.5 Root registration — edit `settings.gradle` (repo root)

Append (after the existing `examples/security/...` block):
```groovy
includeBuild("ai/a2a/a2a-core")
includeBuild("ai/a2a/a2a-core-default")
includeBuild("ai/a2a/a2a-spring-ai")
includeBuild("ai/a2a/a2a-spring-boot-starter")
```

## 4. Verification

```bash
./gradlew :ai:a2a:a2a-core:test :ai:a2a:a2a-core-default:test \
          :ai:a2a:a2a-spring-ai:test :ai:a2a:a2a-spring-boot-starter:test
./gradlew :ai:a2a:a2a-core:checkstyleMain :ai:a2a:a2a-core-default:checkstyleMain \
          :ai:a2a:a2a-spring-ai:checkstyleMain :ai:a2a:a2a-spring-boot-starter:checkstyleMain
./gradlew :ai:a2a:a2a-core:check :ai:a2a:a2a-core-default:check \
          :ai:a2a:a2a-spring-ai:check :ai:a2a:a2a-spring-boot-starter:check
```

All four must BUILD SUCCESSFUL. The spring-ai module also proves that
`spring-ai-client-chat:2.0.1` resolves cleanly (first real dependency-resolution check).

## 5. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

State: modules scaffolded, root registration done, `check` green. Note any dependency
resolution surprises (especially spring-ai). Then run `graphify update .`.

