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
| `examples/` | `db-query-jpa-spec`, `db-query-jooq-spec`, `security-starter`, `security-context-enrichment`, `security-row-level`, `security-mfa`, `security-customization` | `khezy.springboot` |
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

### Example (self-contained) module commands

Unlike library modules, example modules are self-contained builds with their own `settings.gradle` that includes library builds. Run them with `-p`:

```bash
# Build and test an example module
./gradlew -p examples/security/security-starter check

# Run tests only
./gradlew -p examples/security/security-starter test

# Checkstyle
./gradlew -p examples/security/security-starter checkstyleMain checkstyleTest

# Run the application
./gradlew -p examples/security/security-starter bootRun
```

Example modules require library modules as `includeBuild(...)` in their own `settings.gradle`:
```groovy
pluginManagement {
    includeBuild("../../../build-logic")  // relative from examples/security/<module>/
}
includeBuild("../../../rest-api/api-security")
includeBuild("../../../auto-configurations/rest-api/api-security-spring-boot-starter")
rootProject.name = "example-security-<module>"
```

Every example module must also be registered in the root `settings.gradle`:
```groovy
includeBuild("examples/security/security-starter")
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

## Local Publishing & Sample Testing

Changes to `api-security` or `api-security-spring-boot-starter` can be tested against the sample project:

```bash
# 1. Publish both modules to mavenLocal
./gradlew :api-security:publishToMavenLocal :api-security-spring-boot-starter:publishToMavenLocal

# 2. Boot the sample project
cd /home/khezy/Documents/learning/spring/khezy-boot3-sample
./gradlew :api-security-sample:bootRun

# 3. Test endpoints
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user"}'
curl -s http://localhost:8080/ -H "Authorization: Bearer <token>"
```

The sample project uses `mavenLocal()` repository (`repositories { mavenLocal() }`). After publishing updates, the sample picks them up on next build.

## Documentation Tone

Module READMEs should follow the KHEZY mission (`about/khezy-mission-vision.md`):

- **Position the library as a helper, not a replacement** — "sits on top of Spring Security, not instead of it"
- **Never say "without understanding X"** — be honest about what concepts the developer still needs to learn
- **Mention what the library does NOT do** — sets clear boundaries, avoids overpromising
- **Define who it's for** — beginners (working infrastructure without 200 pages of reference), experienced devs (skip boilerplate), bootstrap projects (MVP in a sprint)
- **Mention Spring Boot 4 inspiration where applicable** — e.g., MFA features are inspired by Spring Boot 4 patterns not yet available in Spring Boot 3

## Spring Security Gotchas (critical)

### `FactorAppendingConfigurer.init()` — never call `AuthenticationManagerBuilder.build()`
The `build()` method is a **terminal operation** — the builder can only be built once. Spring Security's `HttpSecurityConfiguration.authenticationManager()` already builds it. Use `AuthenticationConfiguration.getAuthenticationManager()` instead (idempotent — builds once, caches).

### `ClaimBasedFactorExtractor` — must prepend `FACTOR_` prefix
`FactorExtractor.extractFactors()` returns **factor authority strings** (e.g. `"FACTOR_PASSWORD"`), not raw claim values (e.g. `"PASSWORD"`). Use `FactorAuthorities.getFactorAuthorityFromMethod()` before returning.

### `KhezyJwtFilter` — catch `Exception`, not just `TokenException`
If `loadUserByUsername()` throws `UsernameNotFoundException` (or any other non-`TokenException`), the exception propagates to `ExceptionTranslationFilter` → 401. Catch `Exception` broadly in the filter.

### `AutoConfiguration.imports` vs `@Import`
- Entries in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` respect `@ConditionalOnClass`, `@ConditionalOnProperty` etc.
- Classes listed in `@Import({...})` do **NOT** — conditions are bypassed. Put new auto-config classes ONLY in `AutoConfiguration.imports`, never in `@Import` with conditionally-gated classes.

### `@EnableKhezyApiSecurity` + legacy `@EnableMFA` coexistence
`MultiFactorAuthenticationConfig.setImportMetadata()` must read from **both** `@EnableKhezyApiSecurity` (first) and `@EnableMFA` (fallback) since `ImportAware` receives the annotation metadata of the class directly annotated with `@Import`, not intermediate selectors.

### `KhezyApiSecurityImportSelector` — inline validation
Do NOT delegate to `MultiFactorImportSelector` for validation. The inner selector's `selectImports()` receives the metadata of the **original annotated class** (not the outer selector), so it can't read `@EnableKhezyApiSecurity` attributes. Inline all validation in `KhezyApiSecurityImportSelector`.

### CORS bean conflict with `mvcHandlerMappingIntrospector`
Spring Boot's `WebMvcAutoConfiguration` creates a `HandlerMappingIntrospector` bean that also implements `CorsConfigurationSource`. When `KhezyApiSecurityConfig.CorsConfigurationSourceConfiguration` creates another `CorsConfigurationSource`, mark it `@Primary`.

### `OncePerRequestFilter.logger` is JCL, not SLF4J
The inherited `logger` field in `OncePerRequestFilter` uses Jakarta Commons Logging. It does NOT support `{}` format placeholders. Use string concatenation: `logger.debug("msg: " + var)` not `logger.debug("msg: {}", var)`.

### `@P("name")` annotation for SpEL parameter resolution
`DefaultSecurityParameterNameDiscoverer` discovers method parameter names for SpEL expressions. When the `-parameters` compiler flag is not set, use `@P("name")` from `org.springframework.security.core.parameters.P` to make parameter names available in security SpEL expressions:
```java
public Optional<Invoice> findById(final Long id, @P("tenantId") final String tenantId) { ... }
```
This makes `#tenantId` resolvable in `@RowLevelSecurity(expression = "#tenantId")`.

### RowLevelSecurity — CGLIB proxy annotation loss
`RowLevelSecurityPointcut` and `RowLevelSecurityMethodInterceptor` must resolve the target method from the proxy class. CGLIB proxy methods do NOT carry annotations from the target class:
- In `RowLevelSecurityPointcut.matches()`, use `targetClass.getMethod(method.getName(), method.getParameterTypes())` to get the annotated method.
- In `RowLevelSecurityMethodInterceptor.invoke()`, use `AopUtils.getMostSpecificMethod(invocation.getMethod(), AopProxyUtils.ultimateTargetClass(invocation.getThis()))` to resolve the target method.

### RowLevelSecurity — DefaultPointcutAdvisor must use RowLevelSecurityPointcut
Never use `new DefaultPointcutAdvisor(advice)` — this creates a `TruePointcut` that matches ALL beans, causing circular dependency with Spring's transaction infrastructure. Always use:
```java
new DefaultPointcutAdvisor(new RowLevelSecurityPointcut(), advice)
```

### RowLevelSecurity — EntityManager.find() bypasses Hibernate filters
`EntityManager.find()` (used by `JpaRepository.findById()`) does NOT respect Hibernate session filters. Only HQL/JPQL queries respect filters. Use `@Query("select e from Entity e where e.id = :id")` instead of relying on `findById()` when row-level security is needed.

### data.sql runs before Hibernate DDL creation
By default, `data.sql` scripts execute BEFORE Hibernate creates tables from `@Entity` annotations. Add `spring.jpa.defer-datasource-initialization=true` to `application.properties` to defer script execution until after Hibernate DDL.

### Example settings.gradle — must include library builds
Example modules in `examples/security/` must include library builds in their own `settings.gradle` for standalone compilation:
```groovy
includeBuild("../../../rest-api/api-security")
includeBuild("../../../auto-configurations/rest-api/api-security-spring-boot-starter")
```
Otherwise, `./gradlew -p examples/security/<module> compileJava` fails with "Could not find io.github.khezyapp:api-security-spring-boot-starter".

## Test patterns

### Annotation/gotcha tests for auto-configurations
Avoid `ApplicationContextRunner` / `WebApplicationContextRunner` for auto-configs with complex Spring Security dependencies (e.g., need `HttpSecurity` or `AuthenticationConfiguration`). Instead, test class-level annotations via reflection where the bean would require multi-level mock chains.

```java
// Test annotations and method existence without loading full context
@Test
void shouldBeAutoConfiguration() {
    assertThat(MyConfig.class.getAnnotation(AutoConfiguration.class)).isNotNull();
}

@Test
void shouldHaveConditionalOnMissingBean() {
    Arrays.stream(MyConfig.class.getDeclaredMethods())
        .filter(m -> m.getReturnType().equals(MyBean.class))
        .findFirst().orElseThrow()
        .getAnnotation(ConditionalOnMissingBean.class);
}
```

### Array → stream conversion
Java arrays do NOT have a `stream()` method. Use `Arrays.stream(array)` not `array.stream()`.

### Method discovery for package-private methods
`Class.getMethods()` only returns `public` methods. Use `Class.getDeclaredMethods()` to find package-private bean factory methods.
