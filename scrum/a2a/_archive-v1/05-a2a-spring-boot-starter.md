# A2A-05 — `a2a-spring-boot-starter`: auto-configuration, properties, identity

**Prerequisite task files:** `00-plan-overview.md`, `02-a2a-core-ports.md`, `03-a2a-core-default.md`.

**Do NOT modify** other modules. Code lives in `ai/a2a/a2a-spring-boot-starter/src/main/java` (package `io.github.khezyapp.a2a.starter.*`). The HTTP controllers arrive in A2A-06 — do NOT create them here.

---

## 1. Goal

Wire the A2A ports as overridable Spring beans (`@ConditionalOnMissingBean` for every
port), add configuration properties, and implement the security-based
`CallerIdentityResolver` (fixes the community auth TODO). Register the auto-configuration
class in `AutoConfiguration.imports`.

## 2. Context digest

- Design §6: `@ConditionalOnMissingBean` for `A2AAgentExecutor`, `A2ATaskStore`, `A2ARequestDispatcher`, `A2ARemoteAgentClient`, ...; properties under `io.github.khezyapp.a2a.*`; `CallerIdentityResolver` reads Spring Security `Authentication` if present.
- AGENTS.md gotchas that bind here:
  - Auto-config classes go ONLY in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (never `spring.factories`, never `@Import` for conditionally-gated classes).
  - For auto-configs with complex Spring Security deps, test via **reflection/annotation checks**, NOT `ApplicationContextRunner` (AGENTS.md "Test patterns").
  - Jackson 3: `tools.jackson.*` if you serialize anything (A2A-06 will do the serializing).
  - `spring-boot-starter-webmvc-test` is already the test dependency (from the plugin).
- Ports to wire (from `02`/`03`): `A2ATaskStore`, `A2AEventQueue`, `PushNotificationSender`, `CallerIdentityResolver`, `A2ARequestDispatcher`, `A2AAgentExecutor` (user-provided), `Supplier<AgentCard>` (user-provided or built from properties).
- Defaults from `03`: `InMemoryTaskStore`, `InMemoryEventQueue`, `NoopPushNotificationSender`, `DefaultA2ARequestDispatcher`.

## 3. Files to create

### 3.1 `io.github.khezyapp.a2a.starter.A2AProperties` (`@ConfigurationProperties(prefix = "io.github.khezyapp.a2a")`)

```java
// fields (with getters/setters, or compact record if you prefer):
String agentCardName;        // default "Khezy A2A Agent"
String agentCardDescription; // default ""
String agentCardUrl;         // default ""
String agentCardVersion;     // default "1.0.0"
boolean streamingEnabled;    // default true
```
Use `default` values in the class initializers or constructor.

### 3.2 `io.github.khezyapp.a2a.starter.security.SecurityCallerIdentityResolver` implements `CallerIdentityResolver`

- Reads `SecurityContextHolder.getContext().getAuthentication()`.
- If null → `CallerIdentity.anonymous()`.
- Else map: `subject = Optional.ofNullable(authentication.getName())`, `scopes = authorities → Set<String>`, `claims = Map.of("principal", authentication.getPrincipal())`.
- Use `Collections.unmodifiableSet`/`Map.copyOf` for safety.

### 3.3 `io.github.khezyapp.a2a.starter.A2AAutoConfiguration`

- Annotate: `@AutoConfiguration` + `@EnableConfigurationProperties(A2AProperties.class)` + `@ConditionalOnClass({A2AAgentExecutor.class, A2ATaskStore.class})`.
- Beans (each `@ConditionalOnMissingBean`):
  - `A2ATaskStore taskStore()` → `new InMemoryTaskStore()`
  - `A2AEventQueue eventQueue()` → `new InMemoryEventQueue()`
  - `PushNotificationSender pushNotificationSender()` → `new NoopPushNotificationSender()`
  - `Supplier<AgentCard> agentCardSupplier(A2AProperties props)` → builds an `AgentCard` from `AgentCard.builder().name(...).description(...).url(...).version(...).build()` (verify builder component names via `javap org.a2aproject.sdk.spec.AgentCard$Builder`; set only what the builder exposes — `name`, `description`, `url`, `version`, `capabilities` if required).
  - `CallerIdentityResolver callerIdentityResolver()` → `new SecurityCallerIdentityResolver()` annotated `@ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")`.
  - `A2ARequestDispatcher a2aRequestDispatcher(A2AAgentExecutor executor, A2ATaskStore taskStore, A2AEventQueue eventQueue, PushNotificationSender sender, Supplier<AgentCard> cardSupplier)` → `new DefaultA2ARequestDispatcher(executor, taskStore, eventQueue, sender, cardSupplier)`.
- Keep the config class package-private or public as preferred; all `@Bean` factory methods may be package-private (AGENTS.md note: `Class.getDeclaredMethods()` finds them in tests).

### 3.4 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
io.github.khezyapp.a2a.starter.A2AAutoConfiguration
```
(One FQCN per line. A2A-06 adds the web config line.)

## 4. Tests (`src/test/java`) — reflection/annotation pattern (no Spring context)

Per AGENTS.md "Annotation/gotcha tests for auto-configurations":

1. `A2AAutoConfigurationTest`:
   - class annotated `@AutoConfiguration`; `@EnableConfigurationProperties(A2AProperties.class)` present.
   - each `@Bean` factory method (found via `getDeclaredMethods()`, filtered by return type) carries `@ConditionalOnMissingBean`.
   - `CallerIdentityResolver` factory method is annotated `@ConditionalOnClass` when security is on the test classpath.
2. `A2APropertiesTest` — defaults: agentCardName `"Khezy A2A Agent"`, streamingEnabled `true` (plain unit test on a new instance).
3. `SecurityCallerIdentityResolverTest` — set a mock `Authentication` into `SecurityContextHolder` (remember to `SecurityContextHolder.clearContext()` in `@AfterEach`): named principal → subject present; no authentication → `CallerIdentity.anonymous()`.
4. `AutoConfigurationImportsTest` — read `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` from the classpath and assert it contains the A2A auto-config FQCN.

## 5. Verification

```bash
./gradlew :ai:a2a:a2a-spring-boot-starter:test
./gradlew :ai:a2a:a2a-spring-boot-starter:checkstyleMain :ai:a2a:a2a-spring-boot-starter:checkstyleTest
./gradlew :ai:a2a:a2a-spring-boot-starter:check
```

## 6. Handoff note for the next task

> Record this handoff by appending an entry to `99-handoff-log.md` (template inside;
> protocol in `00` §6.7). Chat is NOT the handoff channel.

Document the bean names, the `AgentCard` builder components used, and the exact
`A2AProperties` field set — A2A-06 needs the `A2ARequestDispatcher` bean and the
properties to build the controllers.
Then run `graphify update .`.

