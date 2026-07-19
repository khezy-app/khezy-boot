# Khezy Rest API Security Starter

Spring Boot auto-configuration for the [api-security](../../rest-api/api-security) library. Drop it in and everything wires automatically.

## Quick Start

```groovy
dependencies {
    implementation 'io.github.khezyapp:api-security-spring-boot-starter:1.0.0'
}
```

That's it. The starter scans your context for `AuthorizationRule`, `SecurityContextEnricher`, and `AuthenticationBuilderFactory` beans, then registers the infrastructure behind them.

## What It Does

| Bean | Condition | Purpose |
|------|-----------|---------|
| `AuthenticationBuilderManager` | Always | Iterates registered factories to build/upgrade authentication tokens |
| `KhezyMethodSecurityExpressionHandler` | Always | Powers `@RequiredAuthorizationRule` and `check()` in SpEL |
| `AnnotationTemplateExpressionDefaults` | Always | Enables `@Param` template expressions in annotations |
| `UrlBasedCorsConfigurationSource` | `khezy.api.cors.enabled=true` | Configures CORS from properties |
| 9 × `AuthenticationBuilderFactory` | Each gated by `@ConditionalOnClass` | Provides token builders for every supported auth type |

All beans use `@ConditionalOnMissingBean` — provide your own to override.

## Supported Auth Types (Auto-Registered)

Factories are registered only when the corresponding Spring Security class is on the classpath:

| Factory | Required Class | Factor Authority |
|---------|---------------|-----------------|
| `UsernamePasswordAuthenticationBuilderFactory` | `UsernamePasswordAuthenticationToken` | `FACTOR_PASSWORD` |
| `JwtTokenAuthenticationBuilderFactory` | `JwtAuthenticationToken` | _(none — JWT is not a factor)_ |
| `BearerTokenAuthenticationBuilderFactory` | `BearerTokenAuthentication` | `FACTOR_BEARER` |
| `OAuth2LonginAuthenticationBuilderFactory` | `OAuth2LoginAuthenticationToken` | `FACTOR_AUTHORIZATION_CODE` |
| `Saml2AuthenticationBuilderFactory` | `Saml2Authentication` | `FACTOR_SAML_RESPONSE` |
| `CASTokenAuthenticationBuilderFactory` | `CasAuthenticationToken` | `FACTOR_CAS` |
| `OneTimeTokenAuthenticationBuilderFactory` | `OneTimeTokenAuthenticationToken` | `FACTOR_OTT` |
| `WebAuthnAuthenticationBuilderFactory` | `WebAuthnAuthentication` | `FACTOR_WEBAUTHN` |
| `X509AuthenticationBuilderFactory` | `PreAuthenticatedAuthenticationToken` | `FACTOR_X509` |

Add `spring-boot-starter-oauth2-client`, `spring-security-cas`, etc. to your classpath and the matching factory appears automatically.

## CORS Configuration

Enable via a single property:

```yaml
khezy:
  api:
    cors:
      enabled: true
      allowed-origins: "https://app.example.com"
      allowed-methods: "GET,POST,PUT,DELETE"
      path-pattern: "/api/**"
```

All `khezy.api.cors.*` properties:

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `false` | Master switch — bean is only created when `true` |
| `allow-credentials` | `false` | Include credentials in cross-origin requests |
| `allowed-headers` | `["*"]` | Headers the browser may send |
| `allowed-origins` | _(none)_ | Specific origins allowed |
| `allowed-origin-patterns` | _(none)_ | Origin patterns with wildcards |
| `allowed-methods` | `GET,POST,PUT,DELETE,PATCH,OPTIONS` | HTTP methods allowed |
| `exposed-headers` | _(none)_ | Response headers visible to the browser |
| `allow-private-network` | `false` | Allow localhost-to-public requests |
| `max-age` | _(none)_ | Preflight cache duration in seconds |
| `path-pattern` | _(none)_ | URL pattern to apply CORS to |

## Multi-Factor Authorization (Opt-In)

MFA is **not** auto-configured. Enable it explicitly with `@EnableMFA`:

```java
@EnableMFA(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_WEBAUTHN"})
@Configuration
public class SecurityConfig {}
```

Then implement the factor-requirement lookup:

```java
@Component
public class UserFactorRepository implements RequiredFactorAuthoritiesRepository {
    @Override
    public List<String> findRequiredFactorAuthorities(String username) {
        return List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }
}
```

All authority names **must** start with `FACTOR_`. The starter validates this at startup and fails fast with a clear message.

## Row-Level Security

Auto-configured when JPA is on the classpath. Registers the AOP advisor and interceptor that apply Hibernate filters via `@RowLevelSecurity`.

Gated by `@ConditionalOnBean(EntityManagerFactory.class)` — if you have `spring-boot-starter-data-jpa`, it activates automatically. Otherwise, no RLS beans are created.

See the [api-security README](../../rest-api/api-security/README.md#5-row-level-security) for usage details.

## Bean Override Examples

Override any auto-configured bean by declaring your own:

```java
@Bean
@ConditionalOnMissingBean
AuthenticationBuilderManager customManager() {
    return new MyCustomBuilderManager();
}
```

```java
@Bean
@ConditionalOnMissingBean
KhezyMethodSecurityExpressionHandler customHandler() {
    return new MyCustomExpressionHandler(enrichers, registry);
}
```

## Architecture

```
┌──────────────────────────────────────────────────────┐
│              Your Spring Boot Application             │
│                                                       │
│  @EnableMFA ──────────┐                               │
│  AuthorizationRule ────┤                               │
│  SecurityContextEnricher┤   (optional user beans)      │
│  AuthenticationBuilder ┤                               │
│  Factory               │                               │
└───────────────────────┼──────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────┐
│        api-security-spring-boot-starter                │
│                                                        │
│  KhezySecurityAutoConfiguration                        │
│    ├── KhezyApiSecurityConfig                          │
│    │     ├── AuthenticationBuilderManager              │
│    │     ├── KhezyMethodSecurityExpressionHandler      │
│    │     ├── AnnotationTemplateExpressionDefaults      │
│    │     └── CorsConfigurationSource (conditional)     │
│    └── AuthenticationBuilderFactoryConfig              │
│          └── 9 × Factory beans (classpath-gated)      │
│                                                        │
│  @EnableMFA (opt-in)                                   │
│    └── MultiFactorImportSelector                       │
│          └── MultiFactorAuthenticationConfig           │
│                └── RequiredFactorAuthorityAuthorization │
└───────────────────────────────────────────────────────┘
```

## Modules

| Module | Description |
|--------|-------------|
| `api-security` | Core library — interfaces, builders, registries, expression handler |
| `api-security-spring-boot-starter` | **This module** — auto-configures everything for Spring Boot |

## Requirements

- Java 17+
- Spring Boot 3.x

## Build

```bash
./gradlew :api-security-spring-boot-starter:build
```

## License

KHEZY — Built for Khmer developers, shared with the world.
