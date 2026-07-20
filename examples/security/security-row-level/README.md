# Security Row-Level — Hibernate Row-Level Security

> Automatic data isolation at the database query level using Hibernate filters with the KHEZY starter.

## What You'll Learn

- How `@RowLevelSecurity` declaratively manages Hibernate session filters via AOP
- How to define JPA entities with `@FilterDef` and `@Filter` for tenant isolation
- How SpEL expressions in `@RowLevelSecurity` resolve filter parameter values
- How `SecurityContextEnricher` provides tenant context from HTTP headers
- That `EntityManager.find()` bypasses Hibernate filters — use JPQL queries instead

## Prerequisites

- Java 17+
- Understanding of Hibernate filters (`@FilterDef`, `@Filter`)
- Basic knowledge of JPA and Spring Data repositories
- Familiarity with AOP concepts

## How It Works

### Request Flow

```
HTTP Request (X-Tenant-Id: acme)
    |
    v
KhezyJwtFilter                       -- validates JWT
    |
    v
InvoiceController                    -- extracts X-Tenant-Id, calls service
    |
    v
RowLevelSecurity AOP Advisor (order 600)
    |
    v
RowLevelSecurityMethodInterceptor    -- enables Hibernate filter on Session
    |   session.enableFilter("tenantFilter").setParameter("tenantId", "acme")
    |
    v
InvoiceService.findAll(tenantId)     -- runs with filter active
    |   Hibernate adds: AND tenant_id = 'acme' to every query
    |
    v
InvoiceRepository.findAll()          -- SELECT * FROM invoices
    |   (Hibernate filter transparently adds WHERE clause)
    |
    v
Response                             -- only acme's invoices returned
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `Invoice` entity | JPA entity with `@FilterDef` and `@Filter` for tenant-based row-level security |
| `InvoiceService` | Service methods annotated with `@RowLevelSecurity` |
| `RowLevelSecurityMethodInterceptor` | AOP interceptor enabling/disabling Hibernate filters around method execution |
| `TenantSecurityContextEnricher` | Reads `X-Tenant-Id` header for additional context |
| `InvoiceController` | REST endpoints for invoice access |

### The Hibernate Filter

```java
@Entity
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "invoices")
public class Invoice { ... }
```

The `condition = "tenant_id = :tenantId"` is appended as SQL to every query on this entity when the filter is active. The `:tenantId` parameter is set from the `@RowLevelSecurity` SpEL expression.

### The @RowLevelSecurity Annotation

```java
@RowLevelSecurity(
    filterName = "tenantFilter",     // matches @FilterDef name
    parameterName = "tenantId",      // matches @ParamDef name
    expression = "#tenantId"         // SpEL: resolves to method argument
)
public List<Invoice> findAll(String tenantId) { ... }
```

### Configuration

```properties
khezy.api.security.jwt.secret=my-super-secret-key-that-is-long-enough-for-hmac
khezy.api.security.permit-patterns[0]=/auth/**

spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
```

## Running the Example

### 1. Build and Run

```bash
./gradlew -p examples/security/security-row-level bootRun
```

### 2. Get a Token

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice"}'
```

### 3. Test Row-Level Security

```bash
TOKEN="<token-from-step-2>"

# List invoices in acme tenant → 3 invoices
curl -s http://localhost:8080/invoices \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: acme"

# Try to access globex invoice from acme → 404 (filtered out)
curl -s http://localhost:8080/invoices/4 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: acme"

# Access own invoice → 200
curl -s http://localhost:8080/invoices/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: acme"

# Unknown tenant → empty list
curl -s http://localhost:8080/invoices \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: nonexistent"
```

## What the Library Handles vs What You Implement

### Auto-Configured (zero code)

- `RowLevelSecurityMethodInterceptor` — AOP advisor that manages Hibernate filter lifecycle
- `RowLevelSecurityPointcut` — matches methods annotated with `@RowLevelSecurity`
- Hibernate filter enable/disable — auto-enables before method, disables in `finally` block
- SpEL evaluation — resolves `condition` and `expression` from the annotation

### Your Code

- JPA entity with `@FilterDef` + `@Filter` annotations
- Service methods annotated with `@RowLevelSecurity`
- `SecurityContextEnricher` for tenant context (if needed)
- `UserDetailsService` for authentication

## Important Caveats

- **`EntityManager.find()` bypasses Hibernate filters** — use JPQL queries (`@Query`) for findById operations
- `@RowLevelSecurity` filter applies to all queries executed within the method (repository calls, entity manager queries)
- Filters are disabled in a `finally` block to prevent cross-request leakage
- The `ruleName` delegation mode currently has a ClassCastException bug in the interceptor — use inline SpEL for now

## Reference

- [Hibernate Filter Documentation](https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#filters)
- [Hibernate @FilterDef and @Filter](https://www.baeldung.com/hibernate-filters)
