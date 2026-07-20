# Security MFA — Multi-Factor Authentication

> Stateless MFA for REST APIs following the Spring Security 7 factor authority pattern.

## What You'll Learn

- How `@EnableKhezyApiSecurity(mfAuthorities = {...})` enables MFA with globally required factors
- How `FactorAppendingAuthenticationManager` **auto-injects factor authorities based on `Authentication` token type** — the Spring Security 7 pattern
- How the 9 built-in `AuthenticationBuilderFactory` implementations map each authentication mechanism to its factor authority
- How factor authorities accumulate across authentication steps for step-up scenarios
- How `ClaimBasedFactorExtractor` reads the `factors` claim from JWT tokens for stateless flows
- How `RequiredFactorAuthorityAuthorization` checks that all required factors are present
- How the MFA-aware 403 response (`requiredMFA: true`, `mfaMethod`) tells the frontend what to do next
- How to integrate a custom `AuthenticationManager` with the factor-appending infrastructure

## Prerequisites

- Java 17+
- Understanding of Spring Security's `AuthenticationManager` and `Authentication` token hierarchy
- Understanding of the MFA concept (something you know + something you have)
- Familiarity with Spring Security 7's `FactorGrantedAuthority` pattern

## How Factor Authorities Work (Spring Security 7 Pattern)

### Concept

Spring Security 7 introduces `FactorGrantedAuthority` — every authentication mechanism produces a specific factor authority that gets attached to the `Authentication` object automatically. For example:

| Authentication Mechanism | Token Type | Factor Authority |
|-------------------------|------------|-----------------|
| Username/password login | `UsernamePasswordAuthenticationToken` | `FACTOR_PASSWORD` |
| Bearer token | `BearerTokenAuthentication` | `FACTOR_BEARER` |
| OAuth2 authorization code | `OAuth2LoginAuthenticationToken` | `FACTOR_AUTHORIZATION_CODE` |
| One-time token | `OneTimeTokenAuthenticationToken` | `FACTOR_OTT` |
| WebAuthn / Passkeys | `WebAuthnAuthentication` | `FACTOR_WEBAUTHN` |
| X.509 certificate | `PreAuthenticatedAuthenticationToken` | `FACTOR_X509` |
| SAML2 response | `Saml2Authentication` | `FACTOR_SAML_RESPONSE` |
| CAS | `CasAuthenticationToken` | `FACTOR_CAS` |
| JwtAuthenticationToken | `JwtAuthenticationToken` | _(custom)_ |

When a user authenticates, the system doesn't need to manually assign factors — the factor is derived from **which authentication mechanism was used**.

### How the Library Implements This

The library provides 9 `AuthenticationBuilderFactory` implementations, one per token type. Each factory knows the factor authority for its token type:

```
http.authenticationManager(customManager)
  → User submits password
  → ProviderManager.authenticate() returns UsernamePasswordAuthenticationToken
  → FactorAppendingAuthenticationManager wraps the result
  → Finds UsernamePasswordAuthenticationBuilderFactory (supports the token type)
  → Calls builder.addFactorAuthority() → appends FACTOR_PASSWORD
  → SecurityContextHolder now has: [ROLE_USER, FACTOR_PASSWORD]
```

### Factor Accumulation (Step-Up)

The critical design: `FactorAppendingAuthenticationManager` reads the **current security context** before appending. This means previously accumulated factor authorities are preserved:

```
Step 1: Password authentication
  SecurityContextHolder: [ROLE_USER, FACTOR_PASSWORD]

Step 2: WebAuthn authentication (user now adds a passkey)
  FactorAppendingAuthenticationManager reads current:
    [ROLE_USER, FACTOR_PASSWORD]
  Then appends WebAuthn factor:
    [ROLE_USER, FACTOR_PASSWORD, FACTOR_WEBAUTHN]
  SecurityContextHolder now has both factors
```

This enables stateless step-up without needing the client to carry all factors — the server accumulates them in the session (or in the JWT token for stateless APIs).

### Custom AuthenticationManager Integration

If your application has a custom `AuthenticationManager` (e.g., for LDAP, custom auth providers, or a hand-built `ProviderManager`), you have two options:

**Option A — Let the auto-configuration wrap it:**
`FactorAppendingConfigurer` automatically wraps the default `AuthenticationManager` during `HttpSecurity` initialization. This catches any provider registered via `AuthenticationManagerBuilder`.

**Option B — Manual wrapping:**
```java
@Bean
public AuthenticationManager authenticationManager(
        final AuthenticationConfiguration authConfig,
        final AuthenticationBuilderManager builderManager
) throws Exception {
    final var delegate = authConfig.getAuthenticationManager();
    return new FactorAppendingAuthenticationManager(delegate, builderManager);
}
```

This works with any authentication provider chain — custom providers, LDAP, OAuth2, etc.

### JWT Stateless Flow

For stateless REST APIs (this example), factors come from the JWT token's `factors` claim. The `KhezyJwtFilter` parses the token, and `ClaimBasedFactorExtractor` converts the factor list to `FACTOR_*` authorities:

```
JWT claims: { "factors": ["password", "webauthn"] }
  → ClaimBasedFactorExtractor
  → FactorAuthorities.getFactorAuthorityFromMethod("password") → "FACTOR_PASSWORD"
  → FactorAuthorities.getFactorAuthorityFromMethod("webauthn") → "FACTOR_WEBAUTHN"
  → UsernamePasswordAuthenticationToken with [ROLE_USER, FACTOR_PASSWORD, FACTOR_WEBAUTHN]
  → SecurityContextHolder
```

## MFA Flow (Stateless)

```
Step 1: Password Login
  POST /auth/token {"factors": ["password"]}
  → Returns JWT with factors: ["password"]

Step 2: Access Protected Endpoint
  GET /secure  (Authorization: Bearer <pw-token>)
  → KhezyJwtFilter extracts token
  → ClaimBasedFactorExtractor reads "factors": ["password"]
  → FactorAuthorities converts to FACTOR_PASSWORD
  → SecurityContext set with [ROLE_USER, FACTOR_PASSWORD]
  → RequiredFactorAuthorityAuthorization checks:
      Required from annotation: FACTOR_PASSWORD + FACTOR_WEBAUTHN
      Present in auth:          FACTOR_PASSWORD
      Missing:                  FACTOR_WEBAUTHN
  → 403 with { requiredMFA: true, mfaMethod: "webauthn" }

Step 3: Frontend redirects user to WebAuthn login
  POST /auth/token {"factors": ["password", "webauthn"]}
  → Returns JWT with factors: ["password", "webauthn"]

Step 4: Retry Protected Endpoint
  GET /secure  (Authorization: Bearer <mfa-token>)
  → ClaimBasedFactorExtractor reads ["password", "webauthn"]
  → SecurityContext set with [ROLE_USER, FACTOR_PASSWORD, FACTOR_WEBAUTHN]
  → Required: FACTOR_PASSWORD + FACTOR_WEBAUTHN
  → Present:  FACTOR_PASSWORD + FACTOR_WEBAUTHN
  → 200 OK
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `FactorAppendingAuthenticationManager` | Wraps any `AuthenticationManager`; after authentication, appends factor authority based on token type and preserves previously accumulated factors |
| `AuthenticationBuilderFactory` (9 implementations) | Maps `Authentication` token type to a builder that knows which factor authority to add (e.g., `UsernamePasswordAuthenticationToken` → `FACTOR_PASSWORD`) |
| `AuthenticationBuilderManager` | Iterates factories to find one that supports the token type, applies builder modifications |
| `FactorAppendingConfigurer` | Spring Security `AbstractHttpConfigurer` that wires `FactorAppendingAuthenticationManager` into the filter chain |
| `RequiredFactorAuthorityAuthorization` | `AuthorizationManager` that verifies all required factors are present by combining per-user + global requirements |
| `ClaimBasedFactorExtractor` | Reads `factors` claim from JWT tokens, maps to `FACTOR_*` authorities |
| `RestApiAccessDeniedHandler` | Produces MFA-aware 403 with `requiredMFA: true` |
| `RequiredFactorAuthoritiesRepository` | Interface for per-user MFA requirements from a database |

### Configuration

```properties
khezy.api.security.jwt.secret=my-super-secret-key-that-is-long-enough-for-hmac
khezy.api.security.permit-patterns[0]=/auth/**
khezy.api.security.session-creation-policy=STATELESS
```

### The Annotation

```java
@SpringBootApplication
@EnableKhezyApiSecurity(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_WEBAUTHN"})
public class SecurityMfaApplication { ... }
```

The `mfAuthorities` array defines which factors are globally required. Each authority must start with `FACTOR_`. Built-in constants: `FACTOR_PASSWORD`, `FACTOR_WEBAUTHN`, `FACTOR_OTT`, `FACTOR_BEARER`, `FACTOR_X509`, `FACTOR_CAS`, `FACTOR_SAML_RESPONSE`, `FACTOR_AUTHORIZATION_CODE`.

## Running the Example

### 1. Build and Run

```bash
./gradlew -p examples/security/security-mfa bootRun
```

### 2. Test MFA Flow

```bash
# Step 1: Login with password only → token
PASSWORD_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user","factors":["password"]}' \
  | jq -r '.token')

# Step 2: Try to access secure endpoint → 403 (missing webauthn)
curl -s http://localhost:8080/secure \
  -H "Authorization: Bearer $PASSWORD_TOKEN"
# → {"requiredMFA": true, "mfaMethod": "webauthn", ...}

# Step 3: Complete WebAuthn factor → new token
MFA_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user","factors":["password","webauthn"]}' \
  | jq -r '.token')

# Step 4: Access granted
curl -s http://localhost:8080/secure \
  -H "Authorization: Bearer $MFA_TOKEN"
# → {"message": "Access granted to user", ...}
```

### 3. MFA 403 Response

```json
{
  "type": "about:blank",
  "title": "Access Denied",
  "status": 403,
  "detail": "Additional authentication required",
  "instance": "/secure",
  "requiredMFA": true,
  "mfaMethod": "webauthn"
}
```

## What the Library Handles vs What You Implement

### Auto-Configured (zero code)

- `FactorAppendingConfigurer` — wraps the default `AuthenticationManager` to append factor authorities
- `FactorAppendingAuthenticationManager` — auto-injects factor authority based on `Authentication` token type
- 9 `AuthenticationBuilderFactory` implementations — one per `Authentication` token type
- `AuthenticationBuilderManager` — orchestrates the factory chain
- `RequiredFactorAuthorityAuthorization` — authorization manager that enforces MFA rules
- `RestApiAccessDeniedHandler` — produces MFA-aware 403 responses
- `ClaimBasedFactorExtractor` — reads `factors` claim from JWT tokens
- `KhezyJwtFilter` — orchestration of extract→parse→factor authorities→authenticate

### Your Code

- `@EnableKhezyApiSecurity(mfAuthorities = {...})` — define globally required factors
- `UserDetailsService` for authentication
- Controllers with protected endpoints
- Auth endpoint that generates tokens with `factors` claim (for stateless JWT flow)
- (Optional) `RequiredFactorAuthoritiesRepository` for per-user MFA rules
- (Optional) Custom `AuthenticationBuilderFactory` for custom authentication token types

## How to Adapt for Your Project

1. **Session-based MFA with form login** — The `FactorAppendingAuthenticationManager` automatically appends factor authorities for any authentication that goes through the default `AuthenticationManager`. Enable `formLogin()` and `webAuthn()` in your filter chain, and factors accumulate in the session automatically.

2. **Custom `AuthenticationManager`** — Inject `FactorAppendingAuthenticationManager` as a bean and wrap your custom manager with it. The auto-configured bean is `@ConditionalOnMissingBean`.

3. **Custom authentication token types** — Implement a new `AuthenticationBuilderFactory` for your custom `Authentication` subclass and register it as a `@Bean`. The `AuthenticationBuilderManager` auto-discovers it.

4. **Per-user MFA** — Implement `RequiredFactorAuthoritiesRepository` backed by your database.

5. **Selective MFA** — Use your own `SecurityFilterChain` to apply MFA rules only on specific paths.

What NOT to do:
- Do NOT use `@EnableKhezyApiSecurity` with `mfAuthorities` in a mixed MFA/non-MFA setup without understanding that the `RequiredFactorAuthorityAuthorization` applies to all `anyRequest()` authorization rules when using the default filter chain
- Do NOT forget that stateless JWT flows require factors in each token — each authentication step must produce a token that includes all factors completed so far, or the client will loop on 403 responses

## Reference

- [Spring Security — Multi-Factor Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html)
- [KHEZY — Factor Authorities](https://github.com/khezy-app/khezy-boot)
- [RFC 7807 — Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)
