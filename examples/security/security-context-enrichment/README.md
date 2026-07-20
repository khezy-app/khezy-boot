# Security Context Enrichment — Custom Context + Authorization Rules

> Multi-tenant document API with business-specific authorization rules.

## What You'll Learn

- How `SecurityContextEnricher` injects tenant data from HTTP headers into SpEL expressions
- How `AuthorizationRule` implementations encapsulate business authorization logic
- How `@RequiredAuthorizationRule` declaratively enforces rules on service methods
- How `@RequiredRole` simplifies role-based access control
- How `check('RULE_NAME', params)` bridges SpEL and Java business logic

## Prerequisites

- Java 17+
- Understanding of Spring Security `@PreAuthorize` and SpEL expressions
- Basic knowledge of multi-tenancy patterns

## How It Works

### Request Flow

```
HTTP Request (with X-Tenant-Id header)
    |
    v
KhezyJwtFilter               -- validates JWT, populates SecurityContext
    |
    v
TenantSecurityContextEnricher -- reads X-Tenant-Id header, returns Map of attributes
    |
    v
KhezyMethodSecurityExpressionHandler
    |                         -- merges enricher attributes into SecurityAttributeContext
    v
@RequiredAuthorizationRule   -- expands to @PreAuthorize("check('RULE_NAME', params)")
    |
    v
AuthorizationRule.evaluate() -- business logic checks ownership + tenant
    |
    v
Controller                   -- returns result or 403
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `TenantSecurityContextEnricher` | Reads `X-Tenant-Id` header, injects `tenantId` into SpEL context |
| `DocumentOwnerRule` | Checks user owns the document AND belongs to the same tenant |
| `TenantMemberRule` | Checks user's tenant has documents in the system |
| `DocumentService` | Business logic with `@RequiredAuthorizationRule` and `@RequiredRole` annotations |
| `DocumentController` | REST endpoints for document access |

### Configuration

```properties
khezy.api.security.jwt.secret=my-super-secret-key-that-is-long-enough-for-hmac
khezy.api.security.jwt.expiration=3600
khezy.api.security.permit-patterns[0]=/auth/**
khezy.api.security.session-creation-policy=STATELESS
```

## Running the Example

### 1. Build and Run

```bash
./gradlew -p examples/security/security-context-enrichment bootRun
```

### 2. Get a Token

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice"}'
```

### 3. Test Authorization Rules

```bash
# Alice owns document 1 in acme tenant → 200
curl -s http://localhost:8080/documents/1 \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: acme"

# Bob tries to access Alice's document → 403
curl -s http://localhost:8080/documents/1 \
  -H "Authorization: Bearer <bob-token>" \
  -H "X-Tenant-Id: acme"

# Alice tries to access document from wrong tenant → 403
curl -s http://localhost:8080/documents/1 \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: globex"

# List all documents in acme tenant → 200
curl -s http://localhost:8080/documents \
  -H "Authorization: Bearer <token>" \
  -H "X-Tenant-Id: acme"
```

## What the Library Handles vs What You Implement

### Auto-Configured (zero code)

- `KhezyMethodSecurityExpressionHandler` — collects all `SecurityContextEnricher` and `AuthorizationRule` beans
- `AuthorizationRuleRegistry` — stores and resolves named rules for `check()` expressions
- `@RequiredAuthorizationRule` — meta-annotation that expands to `@PreAuthorize("check(...)")`
- `@RequiredRole` — meta-annotation that expands to `@PreAuthorize("hasAnyRole(...)")`
- Default `SecurityFilterChain` with JWT support

### Your Code

- `SecurityContextEnricher` bean — extract domain data (tenant, department, tier) from request
- `AuthorizationRule` beans — implement business authorization logic
- Service methods with security annotations
- `UserDetailsService` for authentication

## How to Adapt for Your Project

1. **Replace header-based tenant extraction** with JWT claims, session data, or database lookup
2. **Add more `AuthorizationRule` implementations** for complex business rules
3. **Use `hasHeaderValue()`** in SpEL for header-based authorization without custom rules
4. **Combine with `@RowLevelSecurity`** for database-level tenant isolation (see Phase 3)

What NOT to do:
- Do NOT put business logic in controllers — keep it in service methods with annotations
- Do NOT hardcode tenant IDs — use the enriched context from `SecurityContextEnricher`
- Do NOT create rules that depend on request-scoped beans outside of method evaluation

## Reference

- [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [SpEL Expressions in Security](https://docs.spring.io/spring-security/reference/servlet/authorization/expr.html)
