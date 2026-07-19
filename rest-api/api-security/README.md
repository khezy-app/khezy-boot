# Khezy Rest API Security Library

High-level Spring Security abstractions for developers. Build authorization infrastructure with simple interfaces instead of customizing Spring Security from scratch.

## Philosophy

Spring Security is powerful but complex. This library wraps its core concepts behind **minimal, discoverable interfaces** so beginners can:

- Implement **custom authorization rules** without understanding `SecurityExpressionRoot`
- Build **authenticated tokens** without knowing 9 different `Authentication` constructors
- Add **multi-factor authorization** without writing `AuthorizationManager` from scratch
- Apply **row-level security** without plumbing Hibernate filters manually

The library provides **ready infrastructure** — you implement the **logic**.

## Quick Start

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.khezyapp:api-security:1.0.0'
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

Inject request-scoped attributes (tenant, department, subscription tier) into SpEL expressions:

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

The manager iterates registered factories, finds the matching builder for the authentication type, applies your customisations, and returns the upgraded `Authentication`.

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
┌─────────────────────────────────────────────────────────┐
│                    Your Application                      │
│  implements AuthorizationRule, SecurityContextEnricher,  │
│  RowLevelSecurityRule, RequiredFactorAuthoritiesRepo     │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Khezy Rest API Security Library             │
│                                                          │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │ Authorization│  │ Authentication│  │ Multi-Factor    │ │
│  │ Rule Registry│  │ Builder Manager│  │ Authorization   │ │
│  └─────────────┘  └──────────────┘  └─────────────────┘ │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │ Row-Level    │  │ Security     │  │ Encrypted       │ │
│  │ Security AOP │  │ Expression   │  │ OAuth2 Storage  │ │
│  └─────────────┘  └──────────────┘  └─────────────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│               Spring Security (core)                     │
│  AuthenticationManager, SecurityExpressionRoot,          │
│  AuthorizationManager, HttpSecurity, Filter Chains       │
└─────────────────────────────────────────────────────────┘
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
