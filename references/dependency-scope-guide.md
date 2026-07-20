# Spring Boot Library: Dependency Configuration Guide

## Rationale

In a Spring Boot **library** (not an application), dependency scopes determine what the consumer sees on their compile and runtime classpaths. Incorrect scopes either:
- **Force** the consumer to pull in dependencies they don't need (bloated builds)
- **Hide** types that the consumer needs to compile against (build errors)

## Gradle Dependency Configurations (with `java-library` plugin)

| Scope | Consumer Compile | Consumer Runtime | Published in POM | Use Case |
|-------|-----------------|-----------------|------------------|----------|
| `api` | ✅ | ✅ | Transitive | Types in the library's public API (interfaces consumers implement, types in method signatures) |
| `implementation` | ❌ | ✅ | Runtime scope | Internal implementation details; tests still see them |
| `compileOnly` | ❌ | ❌ | Not published | Optional features; consumers who use the feature already have the dependency |
| `runtimeOnly` | ❌ | ✅ | Runtime scope (no compile) | JDBC drivers, logging impls — rarely used in libraries |
| `testImplementation` | ❌ | ❌ | Not published | Test frameworks and test-only dependencies |

## Decision Tree

```
For each dependency:

1. Is the type from this dependency exposed in the library's PUBLIC API?
   (interfaces, abstract classes, method parameters/returns consumers use)
   YES → api
          └─ Example: spring-boot-starter-security
             (Authentication, GrantedAuthority, PreAuthorize)

   NO  → Go to 2.

2. Is this an OPTIONAL feature that some consumers won't use?
   YES → compileOnly
          └─ Example: oauth2-*, cas, saml2, spring-boot-starter-data-jpa
          └─ Requires @ConditionalOnClass guard in auto-configuration
          └─ Test dependencies must be added separately as testImplementation

   NO  → Go to 3.

3. Is this needed ONLY for the library's internal compilation?
   YES → implementation
          └─ Example: spring-boot-starter-aop
          └─ Consumer doesn't need AOP types in their editor

   NO  → testImplementation
          └─ Example: JUnit 5, Mockito, spring-security-test
```

## `compileOnly` Best Practices

### When to use
- The feature is optional (OAuth2, SAML, CAS, JPA, etc.)
- Consumers who use the feature already have the dependency
- The library's auto-configuration uses `@ConditionalOnClass` to guard bean registration

### What to watch for
- **`compileOnly` dependencies are NOT available on test classpath.** If tests use those types, add them as `testImplementation` separately.
- **`compileOnly` dependencies are NOT published in the POM.** Consumers won't be notified of them. Documentation must mention which dependencies are needed for which features.
- **At runtime, the library's classes that reference `compileOnly` types will load only if the consumer has those types on their classpath.** This is safe with `@ConditionalOnClass` guards.

### Example: OAuth2 in api-security
```groovy
// build.gradle
compileOnly 'org.springframework.boot:spring-boot-starter-oauth2-client'

// Tests need them
testImplementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

```java
// Auto-configuration uses @ConditionalOnClass
@Configuration
@ConditionalOnClass(name = "org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken")
public class AuthenticationBuilderFactoryConfig {
    @Bean
    OAuth2LonginAuthenticationBuilderFactory oAuth2LonginAuthenticationBuilderFactory() {
        return new OAuth2LonginAuthenticationBuilderFactory();
    }
}
```

### Example: JPA Row-Level Security in api-security
```groovy
compileOnly 'org.springframework.boot:spring-boot-starter-data-jpa'
```

```java
@Configuration
@ConditionalOnClass(name = "org.hibernate.Session")
public class KhezyRowLevelSecurityConfig {
    // ... JPA-dependent beans
}
```

## `api` Best Practices

### When to use
- The dependency provides types that appear in the library's **public interfaces**
- Consumers MUST have the dependency to compile against the library

### Only Spring Security core qualifies for api-security
The following indicate an `api` dependency:
- Method parameters: `Authentication auth` in `AuthenticationBuilderFactory.supports(Class<?>)`
- Return types: `Authentication build()` in `AuthenticationBuilder.build()`
- Extended classes: `KhezySecurityExpressionRoot extends SecurityExpressionRoot`
- Meta-annotations: `@PreAuthorize` on `@RequiredRole`

## `implementation` Best Practices

### When to use
- Internal infrastructure that consumers don't interact with directly
- Examples: `DefaultPointcutAdvisor`, `SpelExpressionParser`, `MethodInterceptor`

## Removing Unused Dependencies

### How to check
```bash
# Search for imports from each dependency package
grep -r "import com.webauthn4j" src/main/java/   # Empty → safe to remove
grep -r "import io.micrometer.tracing" src/main/java/  # Empty → safe to remove
```

### What to remove
- `webauthn4j-core` — not used (uses Spring Security built-in webauthn types)
- `micrometer-tracing-bridge-otel` — not used (no tracing instrumentation in this module)

## Verifying Dependency Scopes

After changing scopes, always verify:
```bash
# Full build
./gradlew :module:check

# POM generation (check for unexpected transitive deps)
./gradlew :module:generatePomFileForMavenPublication
cat build/publications/maven/pom-default.xml
```

## Summary Checklist for api-security Module

- [x] `spring-boot-starter-security` → `api` (public API types)
- [x] `spring-boot-starter-aop` → `implementation` (internal AOP)
- [x] `oauth2-*` dependencies → `compileOnly` (optional)
- [x] `spring-security-cas` → `compileOnly` (optional)
- [x] `spring-security-saml2-service-provider` → `compileOnly` (optional)
- [x] `spring-boot-starter-data-jpa` → `compileOnly` (optional, already correct)
- [x] Removed unused `webauthn4j-core` and `micrometer-tracing-bridge-otel`
- [x] Test dependencies added for all `compileOnly` types used in tests
- [x] All 111 tests pass
- [x] Checkstyle passes
