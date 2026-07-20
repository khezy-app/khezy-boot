# Khezy Rest API Security Starter

Spring Boot auto-configuration for the [api-security](../../rest-api/api-security)
library. Drop it in and everything wires automatically.

## Quick Start

```groovy
dependencies {
    implementation 'io.github.khezyapp:api-security-spring-boot-starter:1.0.0'
}
```

Then provide a `UserDetailsService` bean (required for JWT authentication) and
optionally set JWT properties to enable the token filter:

```yaml
khezy:
  api:
    security:
      jwt:
        secret: my-secret-key    # enables JWT filter
```

## What This Starter Does

When you add `api-security-spring-boot-starter` to your classpath:

### Auto-configured (always)

| Bean | Class | Condition |
|------|-------|-----------|
| `AuthenticationBuilderManager` | `KhezyApiSecurityConfig` | Always |
| `KhezyMethodSecurityExpressionHandler` | `KhezyApiSecurityConfig` | Always |
| `AnnotationTemplateExpressionDefaults` | `KhezyApiSecurityConfig` | Always |
| `RestApiAccessDeniedHandler` | `KhezyErrorHandlingAutoConfiguration` | Jackson on classpath |
| `RestApiAuthenticationEntryPoint` | `KhezyErrorHandlingAutoConfiguration` | Jackson on classpath |
| `FactorAppendingAuthenticationManager` | `KhezyFactorAppendingAutoConfiguration` | Always |

### Auto-configured (when `khezy.api.security.jwt.secret` is set)

| Bean | Class |
|------|-------|
| `JwtTokenParser` | `KhezyJwtFilterAutoConfiguration` |
| `BearerTokenExtractor` | `KhezyJwtFilterAutoConfiguration` |
| `ClaimBasedFactorExtractor` | `KhezyJwtFilterAutoConfiguration` |
| `KhezyJwtFilter` | `KhezyJwtFilterAutoConfiguration` |

### Auto-configured (when no `SecurityFilterChain` bean exists)

| Bean | Class | Condition |
|------|-------|-----------|
| Default `SecurityFilterChain` | `KhezyDefaultSecurityFilterChainAutoConfiguration` | `khezy.api.security.enabled=true` (default) |

The default chain: CSRF disabled, stateless sessions,
`FactorAppendingConfigurer` applied, optional CORS + JWT filter, permit
patterns from properties.

### Auto-configured (when `@EnableKhezyApiSecurity(mfAuthorities=...)` is used)

| Bean | Class |
|------|-------|
| `RequiredFactorAuthorityAuthorization` | `MultiFactorAuthenticationConfig` |

### Override any bean

Define your own bean of the same type → auto-configuration is skipped.

## JWT Authentication

Set one property to enable JWT authentication:

```yaml
khezy:
  api:
    security:
      jwt:
        secret: my-secret-key    # enables JWT filter when set
```

That's it. The starter auto-configures:

- `JwtTokenParser` — validates and parses JWT tokens (jjwt-based)
- `BearerTokenExtractor` — extracts `Authorization: Bearer <token>` from headers
- `ClaimBasedFactorExtractor` — extracts MFA factors from the `factors` claim
- `KhezyJwtFilter` — populates `SecurityContext` from JWT

You provide a `UserDetailsService` bean (required for user lookup).

### JWT Properties

| Property | Default | Description |
|----------|---------|-------------|
| `khezy.api.security.jwt.secret` | _(none)_ | JWT signing secret (enables filter when set) |
| `khezy.api.security.jwt.issuer` | _(none)_ | Expected issuer claim (validated if set) |
| `khezy.api.security.jwt.expiration` | `3600` | Token TTL in seconds |
| `khezy.api.security.jwt.factors-claim` | `"factors"` | Claim key for MFA factor authorities |

### Override JWT Components

Define your own bean to override any default:

```java
@Bean
TokenParser customTokenParser() {
    return new MyCustomJwtParser();  // your JWT parsing logic
}
```

Supported overrides: `TokenParser`, `TokenExtractor`, `FactorExtractor`,
`KhezyJwtFilter`.

## Error Handling

Auto-configured when Jackson is on the classpath. Returns RFC 7807
`ProblemDetail` JSON responses.

### 401 Unauthorized

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication required"
}
```

### 403 Access Denied

```json
{
  "type": "about:blank",
  "title": "Access Denied",
  "status": 403,
  "detail": "Insufficient permissions"
}
```

### 403 MFA Required

When the denial is caused by missing multi-factor authorities:

```json
{
  "type": "about:blank",
  "title": "Access Denied",
  "status": 403,
  "detail": "Additional authentication required",
  "properties": {
    "requiredMFA": true,
    "mfaMethod": "PASSWORD"
  }
}
```

### Override

Define your own `AccessDeniedHandler` or `AuthenticationEntryPoint` bean.

## Default SecurityFilterChain

A default `SecurityFilterChain` is auto-configured when:

- No `SecurityFilterChain` bean exists in your context
- `khezy.api.security.enabled=true` (default)

The default chain:

- CSRF disabled
- Stateless sessions
- `FactorAppendingConfigurer` applied (wraps `AuthenticationManager` for MFA)
- Permit patterns from `khezy.api.security.permit-patterns` (default: `/auth/**`)
- Error dispatcher permitted
- Optional CORS (when `khezy.api.cors.enabled=true`)
- Optional JWT filter (when `khezy.api.security.jwt.secret` is set)

### Disable Default Chain

Option 1 — set property:

```yaml
khezy:
  api:
    security:
      enabled: false
```

Option 2 — define your own `SecurityFilterChain` bean:

```java
@Bean
SecurityFilterChain customChain(HttpSecurity http) throws Exception {
    return http
        .with(new FactorAppendingConfigurer(), configurer -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(req -> req.anyRequest().authenticated())
        .build();
}
```

### Security Properties

| Property | Default | Description |
|----------|---------|-------------|
| `khezy.api.security.enabled` | `true` | Enable default `SecurityFilterChain` |
| `khezy.api.security.permit-all` | `false` | Permit all requests (dev mode) |
| `khezy.api.security.permit-patterns` | `["/auth/**"]` | URL patterns to permit without auth |
| `khezy.api.security.session-creation-policy` | `STATELESS` | Session creation policy |

## Multi-Factor Authorization (Opt-In)

MFA is **not** auto-configured. Enable it with `@EnableKhezyApiSecurity`:

```java
@SpringBootApplication
@EnableKhezyApiSecurity(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_SECRET_QUESTION"})
public class MyApp { ... }
```

Then implement the factor-requirement lookup:

```java
@Component
public class UserFactorRepository implements RequiredFactorAuthoritiesRepository {
    @Override
    public List<String> findRequiredFactorAuthorities(String username) {
        return List.of("FACTOR_PASSWORD", "FACTOR_SECRET_QUESTION");
    }
}
```

All authority names **must** start with `FACTOR_`. The starter validates this
at startup and fails fast with a clear message.

### How MFA Works

1. `@EnableKhezyApiSecurity(mfAuthorities = {...})` enables
   `RequiredFactorAuthorityAuthorization`
2. `FactorAppendingConfigurer` wraps the `AuthenticationManager` to append
   factor authorities
3. Each authentication step accumulates factor authorities in `SecurityContext`
4. `RequiredFactorAuthorityAuthorization` checks if all required factors are
   present
5. If factors are missing → 403 with `requiredMFA: true` in ProblemDetail
   response

### Override MFA

Define your own `RequiredFactorAuthorityAuthorization` bean to customize MFA
logic.

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

## Row-Level Security

Auto-configured when JPA is on the classpath. Registers the AOP advisor and
interceptor that apply Hibernate filters via `@RowLevelSecurity`.

Gated by `@ConditionalOnBean(EntityManagerFactory.class)` — if you have
`spring-boot-starter-data-jpa`, it activates automatically. Otherwise, no RLS
beans are created.

See the [api-security README](../../rest-api/api-security/README.md#5-row-level-security)
for usage details.

## Supported Auth Types (Auto-Registered)

Factories are registered only when the corresponding Spring Security class is
on the classpath:

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

Add `spring-boot-starter-oauth2-client`, `spring-security-cas`, etc. to your
classpath and the matching factory appears automatically.

## Bean Override Examples

Override any auto-configured bean by declaring your own:

```java
@Bean
AuthenticationBuilderManager customManager() {
    return new MyCustomBuilderManager();
}
```

```java
@Bean
KhezyMethodSecurityExpressionHandler customHandler() {
    return new MyCustomExpressionHandler(enrichers, registry);
}
```

```java
@Bean
TokenParser customTokenParser() {
    return new MyCustomJwtParser();
}
```

```java
@Bean
AccessDeniedHandler customAccessDeniedHandler() {
    return new MyCustomAccessDeniedHandler();
}
```

## Architecture

```
┌───────────────────────────────────────────────────────────┐
│              Your Spring Boot Application                  │
│                                                            │
│  @EnableKhezyApiSecurity ─────┐                            │
│  AuthorizationRule ───────────┤                            │
│  SecurityContextEnricher ─────┤  (optional user beans)     │
│  AuthenticationBuilderFactory┤                            │
│  RequiredFactorAuthoritiesRepo┤                           │
│  UserDetailsService ──────────┤                            │
└───────────────────────────────┼────────────────────────────┘
                                │
┌───────────────────────────────▼────────────────────────────┐
│        api-security-spring-boot-starter                     │
│                                                             │
│  KhezySecurityAutoConfiguration                             │
│    ├── KhezyApiSecurityConfig                               │
│    │     ├── AuthenticationBuilderManager                   │
│    │     ├── KhezyMethodSecurityExpressionHandler           │
│    │     ├── AnnotationTemplateExpressionDefaults           │
│    │     ├── defaultAuthorizationManager (fallback)         │
│    │     └── CorsConfigurationSource (conditional)          │
│    └── AuthenticationBuilderFactoryConfig                   │
│          └── 9 × Factory beans (classpath-gated)           │
│                                                             │
│  KhezyJwtFilterAutoConfiguration (when jwt.secret set)      │
│    ├── JwtTokenParser                                       │
│    ├── BearerTokenExtractor                                 │
│    ├── ClaimBasedFactorExtractor                            │
│    └── KhezyJwtFilter                                       │
│                                                             │
│  KhezyErrorHandlingAutoConfiguration (when Jackson present) │
│    ├── RestApiAccessDeniedHandler (ProblemDetail 403)       │
│    └── RestApiAuthenticationEntryPoint (ProblemDetail 401)  │
│                                                             │
│  KhezyFactorAppendingAutoConfiguration                      │
│    └── FactorAppendingAuthenticationManager                 │
│                                                             │
│  KhezyDefaultSecurityFilterChainAutoConfiguration           │
│    └── defaultSecurityFilterChain (when no custom chain)    │
│          └── FactorAppendingConfigurer applied              │
│                                                             │
│  @EnableKhezyApiSecurity (opt-in)                           │
│    └── KhezyApiSecurityImportSelector                       │
│          └── MultiFactorAuthenticationConfig                │
│                └── RequiredFactorAuthorityAuthorization     │
└─────────────────────────────────────────────────────────────┘
```

## Known Limitations

1. **FactorAppendingConfigurer + custom AuthenticationManager**:
   If you define your own `AuthenticationManager` bean,
   `FactorAppendingConfigurer` will NOT auto-wrap it. Apply it yourself:
   `http.with(new FactorAppendingConfigurer(), configurer -> {})`.

2. **MFA is annotation-driven only**:
   No properties-based default for `RequiredFactorAuthoritiesRepository`. MFA
   requirements vary too much between apps. Use
   `@EnableKhezyApiSecurity(mfAuthorities=...)`.

3. **Custom SecurityFilterChain + MFA**:
   If you define your own `SecurityFilterChain` AND use
   `@EnableKhezyApiSecurity`, apply `FactorAppendingConfigurer` yourself. The
   auto-configuration only applies it to the default chain.

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
