---
name: khezy-coding-style
description: "Coding style and conventions for the khezy-kit project: Java 17+ patterns, Gradle composite build, JUnit 5 test style, module structure, and package naming"
license: MIT
compatibility: opencode
metadata:
  audience: developers
  project: khezy-kit
---

## Project structure

Multi-module Gradle **composite build** — each module is an independent Gradle build with its own `settings.gradle`, `build.gradle`, `group`, `version`. Root `settings.gradle` uses `includeBuild(...)` for all modules (no regular `include`).

| Module | Path | Package prefix |
|---|---|---|
| pluginlib | `utils/pluginlib` | `io.github.khezyapp.pluginlib` |
| string-util | `utils/string-util` | `io.github.khezyapp.stringutil` |
| dynamic-object | `utils/dynamic-object` | `io.github.khezyapp.doa` |
| clone-util | `utils/clone-util` | `io.github.khezyapp.clone` |
| data-masker | `securities/data-masker` | `io.github.khezyapp.datamasker` |
| simple-prompt-template | `templates/simple-prompt-template` | `io.github.khezyapp.prompttemplate` |
| storage-api | `storage/storage-api` | `io.github.khezyapp.storageapi` |
| storage-fs | `storage/storage-fs` | `io.github.khezyapp.storagefs` |

Package naming: module dir may contain hyphens, but **Java package name never does** — strip/compress them (e.g. `data-masker` -> `datamasker`, `string-util` -> `stringutil`, `storage-api` -> `storageapi`, `plugin-lib` -> `pluginlib`).

Build-logic lives in `build-logic/` as a separate included build with convention plugins (`khezy.*`).

## Build system conventions

- **Gradle 8.14.5** wrapper, **JDK 21** toolchain, `--release 17` target bytecode
- Convention plugins in `build-logic/src/main/groovy/`:
  - `khezy.java-library` — **always applied** (aggregates base-java-library + junit5 + maven-publish + code-quality). **Do NOT apply `java-library` directly**.
  - `khezy.java-lombok` — **opt-in** (Lombok 1.18.42, SLF4J 2.0.17); only clone-util and data-masker tests use Lombok
- Checkstyle 13.1.0 always enforced (custom rules in `build-logic/src/main/resources/config/checkstyle/checkstyle.xml`); Javadoc lint suppressed
- Publishing via `com.vanniktech.maven.publish` plugin to Maven Central

### Module `build.gradle` recipe

```groovy
plugins {
    id("khezy.java-library")
    // id("khezy.java-lombok")  // only when Lombok needed
}

group = "io.github.khezyapp.<group-suffix>"
version = "1.0.0"

mavenPublishing {
    pom {
        name = 'Module Name'
        description = """Description."""
    }
}
```

### Module `settings.gradle` recipe

```groovy
pluginManagement {
    includeBuild("../../build-logic")
}
rootProject.name = "<module-name>"
```

## Java coding conventions

### Formatting
- 4-space indentation, no tabs (Checkstyle `FileTabCharacter`)
- 120-char line limit (`LineLength`)
- Egyptian/OTBS brace style (opening brace on same line, closing on its own)
- Braces always required even for single-statement blocks (`NeedBraces`)
- Files end with newline, no trailing whitespace
- UTF-8 encoding

### `final` keyword
- **All method parameters** are `final` (enforced by Checkstyle)
- **All local variables** use `final var` (universal pattern)
- Instance fields are `final` when possible
- Method parameters: `public void foo(final String bar)`

### `var` usage
- **Universal** for local variables, even in complex generic contexts
- Always combined with `final`: `final var x = ...`
- Examples: `final var list = new ArrayList<T>()`, `final var info = cls.getAnnotation(...)`

### Naming

| Element | Convention | Example |
|---|---|---|
| Classes/Interfaces | PascalCase | `PluginManager`, `InMemoryPluginStore` |
| Methods | camelCase | `loadPlugins()`, `loadEager()` |
| Constants | UPPER_SNAKE_CASE | `DEFAULT_VERSION`, `KEY_SEPARATOR` |
| Fields | camelCase | `type`, `dir`, `recursive` |
| Parameters | camelCase | `type`, `dir`, `recursive` |
| Type params | single uppercase | `T`, `K`, `V` |

### Imports
- No star imports in production code (allowed in tests for `Assertions.*`)
- No unused or redundant imports (enforced)
- Standard order: JDK first, then third-party, then project

### Exception handling
- Catch param always `final e`: `} catch (final IOException e) {`
- Wrap checked in `RuntimeException` with descriptive message
- Ignore trivial exceptions with `// ignore` or `// ignore close exception` comment
- No custom exception classes (except `ShellExecutionException` in templates module)

### Nullability
- No JSR-305 annotations (`@Nullable`/`@NonNull`)
- `Objects.requireNonNull(value, "message")` for parameter validation
- `Optional` for return types that may be empty
- `Objects.isNull` / `Objects.nonNull` for conditional checks

### Annotations
- Single annotation per line
- `@Override` on all overriding methods
- `@Test` + `@DisplayName("Human readable")` on every test
- `@SuppressWarnings("unchecked")` for unchecked casts

## Class structure

### Field ordering
1. `private static final` constants
2. `private final` instance fields
3. Mutable instance fields
4. No blank lines between fields usually

### Method ordering
1. Static factory methods / constructors
2. Public API methods (grouped by feature)
3. `@Override` implementations
4. Private helpers (bottom)
5. Inner classes / records / builders (very bottom)

### Constructor patterns
- Records: compact constructors for validation
- Utility classes: `private ClassName() { }`
- Static factory methods preferred: `PluginManager.of(type)` over `new PluginManager<>()`
- `Objects.requireNonNull` for parameter validation
- Builder pattern for configuration-heavy classes (inner `Builder` class, fluent setters, terminal `build()`)

## Key language patterns

- **Records** for data carriers: `public record Foo<T>(String name, T value) { }`
  - Compact constructors for validation + defensive copies
- **Sealed interfaces** with record implementations for discriminated unions
- **`@FunctionalInterface`** for single-method interfaces
- **Strategy pattern**: `support()` + `copy()`/`mask()` method pairs
- **Static facade**: final class, private constructor, static delegation
- **`synchronized`** sparingly (lazy init only)
- **`ConcurrentHashMap`** + **`volatile`** for thread-safe caching
- **`Collections.unmodifiable*`** / **`List.copyOf`** for defensive returns

## Test conventions

- Test class: `{ClassName}Test`, package-private (`class FooTest`)
- Mirrors source package structure
- `@Test` + `@DisplayName("Should ...")` on every test method
- JUnit 5 assertions via static import `org.junit.jupiter.api.Assertions.*`
- `assertEquals(expected, actual)` order
- `@Nested` for grouping related tests
- `@TempDir` for temporary directories
- `@BeforeEach` for setup
- `@ParameterizedTest` + `@MethodSource`, `@ValueSource`, `@CsvSource`
- Anonymous lambda loaders for test PluginLoader: `(PluginLoader<T>) () -> List.of(...)`
- Helper classes as static inner classes in test file
- Helper methods: `private static`
- Helper providers: `static Stream<Arguments> xxxProvider()`

## khezy-boot Spring Security conventions

The `khezy-boot` project is a SEPARATE project from `khezy-kit` but follows the same Java conventions. These sections cover patterns specific to its Spring Security auto-configurations.

### Module layout

| Group | Modules | Plugin |
|---|---|---|
| `rest-api/` | `api-audit`, `api-exception`, `api-security` | `khezy.springboot-library` |
| `auto-configurations/rest-api/` | `api-exception-spring-boot-starter`, `api-security-spring-boot-starter` | `khezy.springboot-library` |
| `db-query/` | `query-grammar`, `query-jpa-spec`, `query-jooq-spec` | `khezy.java-library` |
| `examples/` | `db-query-jpa-spec`, `db-query-jooq-spec` | `khezy.springboot` |

Root `settings.gradle` uses `includeBuild(...)` — each module has its own `settings.gradle` and `build.gradle` but shares the root `gradlew`.

### Auto-configuration patterns

- Put auto-configuration classes in `AutoConfiguration.imports` (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), **never** in `@Import({...})` on another config class.
- `@Import` bypasses `@ConditionalOnClass`, `@ConditionalOnProperty`, etc. — only `AutoConfiguration.imports` respecting them.
- Use `@ConditionalOnMissingBean` on every bean factory method to allow user overrides.
- Gate JWT-dependent configs with `@ConditionalOnClass(name = "io.jsonwebtoken.Claims")` + `@ConditionalOnProperty(prefix = "khezy.api.security.jwt", name = "secret")`.
- Gate Jackson-dependent configs with `@ConditionalOnClass(ObjectMapper.class)`.

### Spring Security critical gotchas

**`FactorAppendingConfigurer.init()`** — NEVER call `AuthenticationManagerBuilder.build()`. It is a terminal operation (builder can only be built once). Use `AuthenticationConfiguration.getAuthenticationManager()` which is idempotent.

```java
// WRONG — builder.build() is terminal:
final var localBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
final var defaultManager = localBuilder.build();  // FAILS if builder already built

// RIGHT — getAuthenticationManager() is idempotent:
final var context = http.getSharedObject(ApplicationContext.class);
final var authConfig = context.getBean(AuthenticationConfiguration.class);
final var defaultManager = authConfig.getAuthenticationManager();
```

**`ClaimBasedFactorExtractor`** — Must prepend `FACTOR_` prefix to claim values. The `FactorExtractor` contract says it returns factor **authority strings**, not raw claim values.

```java
// WRONG — returns raw claim values:
.map(Object::toString).toList();  // returns ["PASSWORD"]

// RIGHT — returns factor authorities:
.map(Object::toString)
.map(FactorAuthorities::getFactorAuthorityFromMethod)
.toList();  // returns ["FACTOR_PASSWORD"]
```

**`KhezyJwtFilter`** — Catch `Exception`, not just `TokenParser.TokenException`. `loadUserByUsername()` can throw `UsernameNotFoundException` which is NOT a `TokenException`.

```java
// WRONG:
catch (final TokenParser.TokenException e) { ... }

// RIGHT:
catch (final Exception e) { ... }
```

**`KhezyApiSecurityImportSelector`** — Do NOT delegate to `MultiFactorImportSelector` for validation. When `KhezyApiSecurityImportSelector` returns `MultiFactorImportSelector.class.getName()`, Spring calls `MultiFactorImportSelector.selectImports()` with the ORIGINAL class metadata (the `@Configuration` class), which has `@EnableKhezyApiSecurity` but NOT `@EnableMFA`. So `MultiFactorImportSelector` can't read `@EnableKhezyApiSecurity` attributes. Inline validation in the outer selector.

**`MultiFactorAuthenticationConfig.setImportMetadata()`** — Must read from both `@EnableKhezyApiSecurity` and `@EnableMFA` since `ImportAware` receives the metadata of the class directly annotated with `@Import`.

```java
@Override
public void setImportMetadata(final AnnotationMetadata importMetadata) {
    final var enableKhezyApiSecurity = importMetadata.getAnnotationAttributes(
            EnableKhezyApiSecurity.class.getName());
    final var enableMFA = importMetadata.getAnnotationAttributes(EnableMFA.class.getName());
    // read from EnableKhezyApiSecurity first, fall back to EnableMFA
}
```

**CORS bean conflict** — Spring Boot's `WebMvcAutoConfiguration` creates a `HandlerMappingIntrospector` bean that also implements `CorsConfigurationSource`. Mark custom `CorsConfigurationSource` beans as `@Primary`.

```java
@Bean
@Primary
@ConditionalOnMissingBean
UrlBasedCorsConfigurationSource apiCorsConfigurationSource(...) { ... }
```

**`OncePerRequestFilter.logger`** — Uses Jakarta Commons Logging, NOT SLF4J. Does not support `{}` format placeholders:

```java
// WRONG:
logger.debug("user: {}", user);  // JCL interprets second arg as Throwable!

// RIGHT:
logger.debug("user: " + user);
```

### Test patterns for auto-configurations

**Avoid `ApplicationContextRunner` for complex Spring Security deps** — For configs requiring `HttpSecurity`, `AuthenticationConfiguration`, or `AuthenticationManagerBuilder`, test annotations and method signatures via reflection instead of loading a full context. These beans need complex multi-level mock chains that are brittle.

```java
// Test auto-config annotation:
@Test
void shouldBeAutoConfiguration() {
    assertThat(MyConfig.class.getAnnotation(AutoConfiguration.class)).isNotNull();
}

// Test @ConditionalOnMissingBean on a package-private factory method:
@Test
void shouldHaveConditionalOnMissingBean() {
    final var method = Arrays.stream(MyConfig.class.getDeclaredMethods())
            .filter(m -> m.getReturnType().equals(MyBean.class))
            .findFirst().orElseThrow();
    assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
}
```

**Array → stream** — Java arrays do NOT have `stream()`. Use `Arrays.stream(array)`.

**Package-private methods** — `Class.getMethods()` only returns `public`. Use `Class.getDeclaredMethods()` to find package-private bean factory methods.

**Dependency mismatch in tests** — When using `ApplicationContextRunner`, `WebApplicationContextRunner` does NOT load all Spring Boot auto-configurations. Only the classes in `AutoConfigurations.of(...)` are loaded. Provide mock beans via `withUserConfiguration()` for missing infrastructure (e.g., `ObjectMapper`, `AuthenticationConfiguration`).

**`Map.of()` vs null values** — `Map.of()` and `List.of()` throw on null elements. For tests with null elements, use mutable collections:

```java
final var list = new ArrayList<String>();
list.add("value");
list.add(null);
list.add("other");
final Map<String, Object> claims = Map.of("key", list);
```

### Publishing and testing workflow

```bash
# 1. Publish both modules to mavenLocal
./gradlew :api-security:publishToMavenLocal :api-security-spring-boot-starter:publishToMavenLocal

# 2. Boot sample project
cd /home/khezy/Documents/learning/spring/khezy-boot3-sample
./gradlew :api-security-sample:bootRun

# 3. Test API
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user"}'
```

The sample project uses `repositories { mavenLocal() }` and picks up published artifacts on next build. The sample's `UserDetailsService` has users "admin"/"admin" (ROLE_ADMIN) and "user"/"user" (ROLE_USER). The secret question map has `("user", "forget-answer")`. Use "user" (not "admin") for testing the secret question endpoint.

### Debugging JWT auth failures

1. Add `logging.level.io.github.khezyapp.api.security: DEBUG` to `application.yml`
2. Check logs for:
   - `Filter 'khezyJwtFilter' configured for use` — confirms filter bean was created
   - `Token present: true` — confirms `BearerTokenExtractor` found the token
   - `JWT parsed successfully for user: X` — confirms token was parsed
   - `SecurityContext set for user: X ... authenticated: true` — confirms auth was set
   - `Before chain.doFilter - auth: X authenticated: true` — confirms auth persisted to downstream filters
3. If 401 occurs after "Before chain.doFilter", the issue is in downstream filters (e.g., `AuthorizationFilter`, `ExceptionTranslationFilter`). Check permit patterns and `authManager`.
4. If 401 occurs and no filter logs appear, the filter might not be in the chain — check `@ConditionalOnProperty` or missing `AutoConfiguration.imports` entry.

## Documentation Tone for Module READMEs

Follow the KHEZY mission when writing or editing module READMEs:

- **Position as helper, not replacement** — "sits on top of Spring Security, not instead of it"
- **Never say "without understanding X"** — be honest about what the developer still needs to learn
- **Mention what the library does NOT do** — clear boundaries, avoids overpromising
- **Define who it's for** — beginners (working infrastructure without deep reference), experienced devs (skip boilerplate), bootstrap projects (MVP in a sprint)
- **Mention Spring Boot 4 inspiration where applicable** — e.g., MFA features are inspired by Spring Boot 4 patterns not yet available in Spring Boot 3
- **The library embeds best practices by default** — beginners benefit automatically even without knowing the best practice

## khezy-boot example module patterns

### Security example module structure

Each security example lives under `examples/security/<module>/` and follows this structure:

```
examples/security/<module>/
├── build.gradle                              # id 'khezy.springboot'
├── settings.gradle                           # includeBuild for library modules
├── README.md                                 # Full documentation
└── src/main/
    ├── java/io/github/khezyapp/examples/security/<module>/
    │   ├── <Module>Application.java          # @SpringBootApplication + @EnableKhezyApiSecurity
    │   ├── config/                           # SecurityConfig, custom configs
    │   ├── controller/                       # REST endpoints + auth controller
    │   └── ...
    └── resources/
        └── application.properties
```

### Example build.gradle recipe

```groovy
plugins {
    id 'khezy.springboot'
}

group = 'io.github.khezyapp'
version = '0.0.1-SNAPSHOT'
description = 'Short description'

dependencies {
    implementation "${group}:api-security-spring-boot-starter"
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // jjwt needed only if demo generates tokens:
    implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
    // JPA needed only for row-level security:
    // implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    // runtimeOnly 'com.h2database:h2'
}
```

### Example settings.gradle recipe

```groovy
pluginManagement {
    includeBuild("../../../build-logic")       // relative from examples/security/<module>
}
includeBuild("../../../rest-api/api-security")
includeBuild("../../../auto-configurations/rest-api/api-security-spring-boot-starter")
rootProject.name = "example-security-<module>"
```

### Example registration in root settings.gradle

Every security example must be registered in the root `settings.gradle`:
```groovy
includeBuild("examples/security/security-starter")
```

### Running example commands

```bash
# Build and test
./gradlew -p examples/security/<module> check

# Just tests
./gradlew -p examples/security/<module> test

# Just checkstyle
./gradlew -p examples/security/<module> checkstyleMain checkstyleTest

# Run the Spring Boot app
./gradlew -p examples/security/<module> bootRun
```

### Example type conventions

| Package | Conventions |
|---------|------------|
| `examples/security/security-starter` | Beginner: JWT auth, default filter chain, error handling |
| `examples/security/security-context-enrichment` | Intermediate: SecurityContextEnricher, AuthorizationRule |
| `examples/security/security-row-level` | Advanced: @RowLevelSecurity with Hibernate filters |
| `examples/security/security-mfa` | Intermediate: factor authorities, step-up MFA |
| `examples/security/security-customization` | Advanced: override all defaults, custom components |

## khezy-boot RowLevelSecurity gotchas

### CGLIB proxy loses annotations (critical)
CGLIB proxy methods do NOT carry `@RowLevelSecurity` annotations from the target class. Fix in two places:

**Pointcut** (`RowLevelSecurityPointcut`):
```java
// WRONG — proxy method has no annotation:
return AnnotatedElementUtils.hasAnnotation(method, RowLevelSecurity.class);

// RIGHT — resolve method from target class:
try {
    final var targetMethod = targetClass.getMethod(method.getName(), method.getParameterTypes());
    return AnnotatedElementUtils.hasAnnotation(targetMethod, RowLevelSecurity.class);
} catch (final NoSuchMethodException e) {
    return false;
}
```

**Interceptor** (`RowLevelSecurityMethodInterceptor`):
```java
// WRONG — invocation.getMethod() is the proxy method:
final var method = invocation.getMethod();
final var annotations = AnnotatedElementUtils.findAllMergedAnnotations(method, RowLevelSecurity.class);

// RIGHT — resolve against target class:
final var method = AopUtils.getMostSpecificMethod(
    invocation.getMethod(),
    AopProxyUtils.ultimateTargetClass(invocation.getThis())
);
final var annotations = AnnotatedElementUtils.findAllMergedAnnotations(method, RowLevelSecurity.class);
```

### DefaultPointcutAdvisor must use RowLevelSecurityPointcut
The auto-configuration must use `new DefaultPointcutAdvisor(new RowLevelSecurityPointcut(), advice)`.
Using the default constructor `new DefaultPointcutAdvisor(advice)` creates a `TruePointcut` that matches ALL beans, causing a circular dependency with Spring's `internalTransactionAdvisor`.

### EntityManager.find() bypasses Hibernate filters
`EntityManager.find()` and `JpaRepository.findById()` do NOT respect Hibernate session filters. Only HQL/JPQL queries respect filters. For row-level security with `findById`, create a custom `@Query` method:
```java
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdWithQuery(@Param("id") Long id);
}
```

### data.sql + Hibernate DDL ordering
By default, `data.sql` runs BEFORE Hibernate DDL creates tables from `@Entity` annotations. Add this property to defer script execution:
```properties
spring.jpa.defer-datasource-initialization=true
```
This also applies to Spring Boot tests loading `data.sql`.

## khezy-boot MFA / Factor Authority patterns

### Factor authority auto-injection
The `FactorAppendingAuthenticationManager` wraps any `AuthenticationManager`. After authentication succeeds, it checks the `Authentication` token type and uses the matching `AuthenticationBuilderFactory` to append the correct factor authority:

| Token Type | Factory | Factor Authority |
|------------|---------|-----------------|
| `UsernamePasswordAuthenticationToken` | `UsernamePasswordAuthenticationBuilderFactory` | `FACTOR_PASSWORD` |
| `BearerTokenAuthentication` | `BearerTokenAuthenticationBuilderFactory` | `FACTOR_BEARER` |
| `JwtAuthenticationToken` | `JwtTokenAuthenticationBuilderFactory` | (none) |
| `OneTimeTokenAuthenticationToken` | `OneTimeTokenAuthenticationBuilderFactory` | `FACTOR_OTT` |
| `WebAuthnAuthentication` | `WebAuthnAuthenticationBuilderFactory` | `FACTOR_WEBAUTHN` |
| `Saml2Authentication` | `Saml2AuthenticationBuilderFactory` | `FACTOR_SAML_RESPONSE` |
| `CasAuthenticationToken` | `CASTokenAuthenticationBuilderFactory` | `FACTOR_CAS` |
| `OAuth2LoginAuthenticationToken` | `OAuth2LonginAuthenticationBuilderFactory` | `FACTOR_AUTHORIZATION_CODE` |
| `PreAuthenticatedAuthenticationToken` | `X509AuthenticationBuilderFactory` | `FACTOR_X509` |

### Factor accumulation for step-up
`FactorAppendingAuthenticationManager.authenticate()` reads `SecurityContextHolder.getContext().getAuthentication()` before appending, preserving previously accumulated factor authorities from prior authentication steps. This enables step-up scenarios.

### Custom AuthenticationManager integration
Inject `FactorAppendingAuthenticationManager` as a bean to wrap custom `AuthenticationManager` implementations:
```java
@Bean
AuthenticationManager authenticationManager(
        final AuthenticationConfiguration authConfig,
        final AuthenticationBuilderManager builderManager
) throws Exception {
    final var delegate = authConfig.getAuthenticationManager();
    return new FactorAppendingAuthenticationManager(delegate, builderManager);
}
```

### Stateless JWT MFA flow
For stateless REST APIs, factors come from JWT `factors` claim via `ClaimBasedFactorExtractor`. The JWT must carry all factors completed so far (e.g., `["password"]` → 403 → user completes webauthn → `["password", "webauthn"]` → 200). Without proper factor accumulation in the token, the client loops on 403.

### Required factor sources
`RequiredFactorAuthorityAuthorization.authorize()` combines required factors from two sources:
1. **Global**: `mfAuthorities` from `@EnableKhezyApiSecurity` annotation
2. **Per-user**: `RequiredFactorAuthoritiesRepository.findRequiredFactorAuthorities(username)`
Access is granted only when both sets of required factors are present in the current authentication's granted authorities.
