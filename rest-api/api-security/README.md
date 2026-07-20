# Khezy Rest API Security Library

High-level Spring Security abstractions for developers. Build authorization
infrastructure with simple interfaces instead of customizing Spring Security
from scratch.

## Philosophy

Spring Security is powerful but verbose. Most tutorials show you one
authentication method — then leave you to figure out JWT pipelines, MFA
step-up flows, and row-level security on your own.

KHEZY sits **on top of** Spring Security, not instead of it. Our goal is
not to compete with the framework but to make it simpler to use: helper
classes that embed best practices and sensible defaults so that even
beginners — who may not yet know what a "best practice" is — benefit
automatically when they adopt this library.

Some features, like multi-factor authorization, are inspired by
approaches in Spring Boot 4. Where those patterns are not yet available
in Spring Boot 3, we bring them forward so developers can use them today.

### What this library provides

- **Named authorization rules** — replace inline SpEL with testable,
  reusable `AuthorizationRule` implementations
- **JWT authentication pipeline** — a pluggable extract → parse → build
  filter that handles the 9 different `Authentication` subtypes for you
- **Multi-factor authorization** — a complete step-up flow with per-user
  factor requirements, per-type token reconstruction, and structured error
  responses telling the client which factor to present next
- **Row-level security** — a single `@RowLevelSecurity` annotation that
  manages Hibernate filter enable/disable lifecycle via AOP
- **Security context enrichment** — inject tenant, department, or custom
  attributes into SpEL expressions through a simple interface

### What this library does NOT do

This library does not replace understanding Spring Security. You still need
to know:

- How `@PreAuthorize` and SpEL method-security expressions work
- The difference between 401 (unauthenticated) and 403 (authenticated but
  forbidden)
- What `Authentication` types your pipeline produces and why
- Hibernate `@FilterDef` / `@Filter` for row-level security
- JWT structure and signing for token-based auth

### Who is this for?

**Beginners** — get a working security infrastructure without reading 200 pages
of Spring Security reference. The defaults work out of the box; customise as you
grow.

**Experienced developers** — skip the boilerplate of wiring MFA pipelines,
OAuth2 encryption, and error handlers. You already understand the concepts;
we give you the production-ready scaffolding.

**Bootstrap projects** — need JWT auth + role-based access + MFA in a sprint?
This starter gets you there. Replace pieces with your own implementations when
your requirements outgrow the defaults.

## Quick Start

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.khezyapp:api-security:1.0.0'
}
```

## Token API (JWT Authentication Pipeline)

Pluggable interfaces for extracting, parsing, and enriching security tokens
from HTTP requests.

### Interfaces

| Interface | Package | Purpose |
|-----------|---------|---------|
| `TokenExtractor` | `token` | Extract raw token from `HttpServletRequest` |
| `TokenParser` | `token` | Parse/validate token → `ParsedToken` (subject, claims, authorities) |
| `FactorExtractor` | `token` | Extract MFA factor authorities from claims |

### Default Implementations

| Class | Package | Activated When |
|-------|---------|---------------|
| `BearerTokenExtractor` | `token.extractor` | Always available |
| `ClaimBasedFactorExtractor` | `token.extractor` | Always available (reads `factors` claim by default) |
| `JwtTokenParser` | `token.parser` | `io.jsonwebtoken` on classpath |

### How the Pipeline Works

```
HTTP Request
    │
    ▼
TokenExtractor.extract(request)  →  Optional<String> token
    │
    ▼
TokenParser.parse(token)         →  ParsedToken(subject, claims, authorities)
    │
    ▼
FactorExtractor.extractFactors(claims)  →  List<String> factorAuthorities
    │
    ▼
SecurityContextHolder.setAuthentication(...)
```

### KhezyJwtFilter

A `OncePerRequestFilter` that orchestrates the full pipeline:

1. Extracts token via `TokenExtractor`
2. Parses JWT via `TokenParser` → gets subject, claims, granted authorities
3. Loads `UserDetails` via `UserDetailsService`
4. Extracts MFA factor authorities via `FactorExtractor`
5. Merges granted authorities + factor authorities into `SecurityContextHolder`

Catches all exceptions broadly — unauthenticated requests continue without
error (the filter returns 401 later via `AuthenticationEntryPoint`).

Override by defining your own `OncePerRequestFilter` bean.

### RestApiAuthenticationEntryPoint

Returns RFC 7807 `ProblemDetail` JSON for 401 responses:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication required"
}
```

## Consumer-Facing APIs

### 1. Custom Authorization Rules

Implement `AuthorizationRule` to encapsulate business permission checks:

```java
@Component
public class DocumentOwnerRule implements AuthorizationRule {
    @Override
    public String getName() {
        return "DOCUMENT_OWNER";
    }

    @Override
    public boolean evaluate(KhezySecurityExpressionRoot root, Object[] args) {
        // args[0] = documentId from the annotation
        return DocumentOwnerService.isOwner(root.getAuthentication().getName(), args[0]);
    }
}
```

Use it declaratively:

```java
@RequiredAuthorizationRule(ruleName = "DOCUMENT_OWNER", params = "#docId")
public Document getDocument(@Param("docId") Long docId) { ... }
```

### 2. Security Context Enrichment

Inject request-scoped attributes (tenant, department, subscription tier) into
SpEL expressions:

```java
@Component
public class TenantEnricher implements SecurityContextEnricher {
    @Override
    public Map<String, Object> getAdditionalContext() {
        return Map.of("tenantId", TenantContext.getCurrentTenant());
    }
}
```

### 3. Authentication Builder

Migrate authentication tokens between types and attach factor authorities:

```java
authenticationBuilderManager.build(currentAuth, builder ->
    builder.authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
           .addFactorAuthority()
);
```

The manager iterates registered factories, finds the matching builder for the
authentication type, applies your customisations, and returns the upgraded
`Authentication`.

Supported types:

- `UsernamePasswordAuthenticationToken` (+ `FACTOR_PASSWORD`)
- `JwtAuthenticationToken` (no factor — JWT is not a factor method)
- `BearerTokenAuthentication` (+ `FACTOR_BEARER`)
- `OAuth2LoginAuthenticationToken` (+ `FACTOR_AUTHORIZATION_CODE`)
- `Saml2Authentication` (+ `FACTOR_SAML_RESPONSE`)
- `CasAuthenticationToken` (+ `FACTOR_CAS`)
- `OneTimeTokenAuthenticationToken` (+ `FACTOR_OTT`)
- `WebAuthnAuthentication` (+ `FACTOR_WEBAUTHN`)
- `PreAuthenticatedAuthenticationToken` (+ `FACTOR_X509`)

### 4. Multi-Factor Authorization

Annotate endpoints that require specific authentication factors:

```java
@EnableMFA(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_WEBAUTHN"})
@Configuration
public class MfaConfig {}
```

Implement the factor requirement lookup:

```java
@Component
public class UserFactorRepository implements RequiredFactorAuthoritiesRepository {
    @Override
    public List<String> findRequiredFactorAuthorities(String username) {
        // Return which factors this user must present
        return List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }
}
```

### 5. Row-Level Security

Apply Hibernate filters declaratively:

```java
@RowLevelSecurity(
    filterName = "tenantFilter",
    parameterName = "tenantId",
    expression = "@tenantEnricher.currentTenant"
)
public List<Document> findAll() { ... }
```

Or delegate to a programmatic rule:

```java
@Component
public class TenantRule implements RowLevelSecurityRule {
    @Override
    public String getName() { return "TENANT_FILTER"; }

    @Override
    public void enableFilter(KhezySecurityExpressionRoot root,
                             RowLevelSecurity annotation,
                             Object[] args) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter(annotation.filterName());
        filter.setParameter(annotation.parameterName(), TenantContext.getCurrentTenant());
    }
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Application                          │
│  implements AuthorizationRule, SecurityContextEnricher,     │
│  RowLevelSecurityRule, RequiredFactorAuthoritiesRepo,       │
│  TokenExtractor, TokenParser, FactorExtractor               │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              Khezy Rest API Security Library                 │
│                                                              │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ Token Pipeline   │  │ Authorization    │                  │
│  │ Extract → Parse  │  │ Rule Registry    │                  │
│  │ → Enrich         │  │                  │                  │
│  └─────────────────┘  └──────────────────┘                  │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ Authentication   │  │ Multi-Factor     │                  │
│  │ Builder Manager  │  │ Authorization    │                  │
│  └─────────────────┘  └──────────────────┘                  │
│  ┌─────────────────┐  ┌──────────────────┐                  │
│  │ Row-Level        │  │ Security         │                  │
│  │ Security AOP     │  │ Expression       │                  │
│  └─────────────────┘  └──────────────────┘                  │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│               Spring Security (core)                         │
│  AuthenticationManager, SecurityExpressionRoot,              │
│  AuthorizationManager, HttpSecurity, Filter Chains           │
└─────────────────────────────────────────────────────────────┘
```

## Extension Points

| Interface | Package | What You Provide |
|-----------|---------|-----------------|
| `AuthorizationRule` | `api` | Business permission check (e.g., "is user owner of document?") |
| `SecurityContextEnricher` | `api` | Extra attributes for SpEL expressions (e.g., tenant ID) |
| `RowLevelSecurityRule` | `api` | Programmatic Hibernate filter enablement |
| `AuthenticationBuilderFactory` | `token.factory` | Factory for custom authentication types |
| `RequiredFactorAuthoritiesRepository` | `authority` | Per-user MFA factor requirements |
| `EncryptorRegistry` | `crypto` | Custom encryptor source for token persistence |
| `TokenExtractor` | `token` | Extract raw token from HTTP request |
| `TokenParser` | `token` | Parse/validate JWT tokens |
| `FactorExtractor` | `token` | Extract MFA factor authorities from claims |

## Modules

| Module | Description |
|--------|-------------|
| `api-security` | This library — interfaces, builders, registries, expression handler |
| `api-security-spring-boot-starter` | Auto-configuration that wires everything into a Spring Boot application |

Add the starter to auto-configure:

```groovy
implementation 'io.github.khezyapp:api-security-spring-boot-starter:1.0.0'
```

## Requirements

- Java 17+
- Spring Boot 3.x (managed via BOM from `khezy.springboot-library`)

## Build

```bash
./gradlew :api-security:build
```

## License

KHEZY — Built for Khmer developers, shared with the world.
