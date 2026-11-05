# CRYPTO-01 — Module skeleton and root registration

**Prerequisites:** `00`.
**Design:** §9.

## 1. Goal

Create the `khezy-crypto` core module and the optional Spring Boot starter, wire them into
the composite build, and leave them compiling with package placeholders only.

## 2. Work

### 2.1 `security/crypto/khezy-crypto`

- `settings.gradle`:
  ```groovy
  pluginManagement { includeBuild("../../../build-logic") }
  rootProject.name = "khezy-crypto"
  ```
- `build.gradle`:
  ```groovy
  plugins { id("khezy.java-library") }
  group = "io.github.khezyapp"
  version = "1.0.0"

  mavenPublishing {
      pom {
          name = "Khezy Crypto"
          description = """
          Application-level encryption utility for sensitive data at rest: AES-256-GCM,
          versioned ciphertext, pluggable key providers, and first-class key rotation."""
      }
  }
  ```
- Create package dirs:
  `io/github/khezyapp/crypto/{contract,default,rotation,exception}`.
- Create one marker class `CryptoModule` in the root package with a private constructor?
  **No** — delete all placeholder markers. Empty source roots are fine until task 02.
- `README.md` — short purpose + link to `scrum/crypto/crypto-library-design.md`; full
  docs in task 06.

### 2.2 `auto-configurations/security/crypto-spring-boot-starter`

- `settings.gradle`:
  ```groovy
  pluginManagement { includeBuild("../../../build-logic") }
  rootProject.name = "crypto-spring-boot-starter"
  ```
- `build.gradle`:
  ```groovy
  plugins { id("khezy.springboot-library") }
  group = "io.github.khezyapp"
  version = "1.0.0"

  ext { cryptoVersion = "1.0.0" }

  dependencies {
      api "io.github.khezyapp:khezy-crypto:${cryptoVersion}"
      annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
  }
  ```
- Create `io/github/khezyapp/crypto/spring/` and
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  Leave the imports file empty until task 05 (or omit it until then).
- `README.md` stub.

### 2.3 Root registration and index

- Add `includeBuild(...)` lines to root `settings.gradle` after the existing security
  modules.
- Add `scrum/crypto/README.md`:
  - design link,
  - action plan link,
  - one-line OWASP/NIST basis.
- Do not modify any existing module in this task.

## 3. Verification

```bash
./gradlew :khezy-crypto:compileJava :crypto-spring-boot-starter:compileJava
./gradlew :khezy-crypto:tasks --all | head
```

Both compile (empty), and the builds resolve against `build-logic`.

## 4. Handoff

Record the exact root registrations, artifact coordinates, and any Gradle settings
surprises. `graphify update .`.
