# Security Starter — Plug-and-Play JWT

> Working JWT security in under 5 minutes with zero custom security code.

## What You'll Learn

- How the starter auto-configures a complete JWT authentication pipeline with just properties
- How `@EnableKhezyApiSecurity` activates method-level security and the default filter chain
- How RFC 7807 `ProblemDetail` responses replace boilerplate error handling
- How permit patterns let unauthenticated requests through to specific endpoints
- What the library handles vs what you need to provide

## Prerequisites

- Java 17+
- Basic understanding of JWT (JSON Web Tokens)
- Basic understanding of Spring Security concepts (authentication, filters, `UserDetailsService`)

## How It Works

### Request Flow

```
HTTP Request
    |
    v
BearerTokenExtractor          -- extracts token from "Authorization: Bearer <token>"
    |
    v
JwtTokenParser                -- verifies HMAC signature, parses claims
    |
    v
UserDetailsService            -- loads user details (you provide this)
    |
    v
ClaimBasedFactorExtractor     -- reads "factors" claim (optional MFA support)
    |
    v
SecurityContextHolder         -- populated with authentication + authorities
    |
    v
Controller                    -- your business logic
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `SecurityStarterApplication` | Main class with `@EnableKhezyApiSecurity` — activates everything |
| `SecurityConfig` | Provides `UserDetailsService` with in-memory users (you provide this) |
| `AuthController` | Demo endpoint that generates JWT tokens (for testing only) |
| `HomeController` | Protected endpoint returning a greeting for the authenticated user |

### Configuration

```properties
# The HMAC secret activates the JWT filter
khezy.api.security.jwt.secret=my-super-secret-key-that-is-long-enough-for-hmac

# Token expiration in seconds (default: 3600 = 1 hour)
khezy.api.security.jwt.expiration=3600

# Permit auth endpoints without authentication
khezy.api.security.permit-patterns[0]=/auth/**

# Session policy (default: STATELESS for REST APIs)
khezy.api.security.session-creation-policy=STATELESS
```

Setting `khezy.api.security.jwt.secret` is the only thing that activates the JWT filter.
Without it, the filter chain still works but no JWT validation occurs.

## Running the Example

### 1. Build and Run

```bash
# From the root project
./gradlew -p examples/security/security-starter bootRun
```

### 2. Get a Token

```bash
curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user"}'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 3. Access Protected Endpoint

```bash
# With valid token → 200
curl -s http://localhost:8080/ \
  -H "Authorization: Bearer <token>"

# Without token → 401
curl -s http://localhost:8080/

# With invalid token → 401
curl -s http://localhost:8080/ \
  -H "Authorization: Bearer invalid-token"
```

### 4. Error Responses (RFC 7807)

Unauthenticated request returns:
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication required",
  "instance": "/"
}
```

## What the Library Handles vs What You Implement

### Auto-Configured (zero code)

- `JwtTokenParser` — validates JWT signatures and expiration
- `BearerTokenExtractor` — reads `Authorization: Bearer` header
- `KhezyJwtFilter` — orchestrates the full extract→parse→load pipeline
- `RestApiAuthenticationEntryPoint` — RFC 7807 JSON for 401
- `RestApiAccessDeniedHandler` — RFC 7807 JSON for 403
- `FactorAppendingConfigurer` — MFA-ready filter chain (ready for step-up auth)
- `AuthenticationBuilderManager` — token reconstruction with factor authorities
- Default `SecurityFilterChain` — CSRF disabled, stateless sessions, permit patterns

### Your Code

- `UserDetailsService` bean — loads user details from your data store
- `application.properties` — JWT secret, permit patterns, session policy
- Controllers — your business endpoints

## How to Adapt for Your Project

1. **Replace `InMemoryUserDetailsManager`** with your real data store (JPA, LDAP, etc.)
2. **Generate real JWT tokens** in your auth endpoint (this demo uses jjwt directly)
3. **Add `@PreAuthorize`** on methods that need role-based access control
4. **Set a strong JWT secret** — the demo key is for testing only

What NOT to do:
- Do NOT store passwords in plain text (use `BCryptPasswordEncoder`)
- Do NOT use `withDefaultPasswordEncoder()` in production
- Do NOT hardcode JWT secrets in source code (use environment variables)

## Reference

- [Spring Security — Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [jjwt — Usage](https://github.com/jwtk/jjwt#usage)
- [RFC 7807 — Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)
