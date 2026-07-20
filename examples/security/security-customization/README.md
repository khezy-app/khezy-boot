# Security Customization — Override & Extend Default Config

> Full control with custom components while still leveraging the library's infrastructure.

## What You'll Learn

- How to define a **custom `SecurityFilterChain`** bean to replace the default auto-configured chain
- How to implement a **custom `TokenExtractor`** that reads tokens from a non-standard header (`X-Auth-Token`)
- How to implement a **custom `FactorExtractor`** that reads MFA claims from a different claim name (`mfa_claims`)
- How to customize **error responses** (401 and 403) with a different JSON format
- How the `@ConditionalOnMissingBean` pattern lets you override individual auto-configured components
- How to **selectively apply MFA** only to specific paths instead of globally

## Prerequisites

- Java 17+
- Understanding of Spring Security auto-configuration
- Familiarity with the KHEZY library's component architecture
- Completed Phase 1 and Phase 4 examples

## Override Strategy

The library uses `@ConditionalOnMissingBean` on every auto-configured component. This means:
1. Define your own `@Bean` of the same type → the library skips its default
2. Don't define it → the library provides a production-ready default

### Override Table

| Auto-Configured Bean | Your Bean Replaces It By |
|---------------------|--------------------------|
| `SecurityFilterChain` | Defining a `SecurityFilterChain` `@Bean` |
| `BearerTokenExtractor` | Defining a `TokenExtractor` `@Bean` |
| `ClaimBasedFactorExtractor` | Defining a `FactorExtractor` `@Bean` |
| `RestApiAccessDeniedHandler` | Defining an `AccessDeniedHandler` `@Bean` |
| `RestApiAuthenticationEntryPoint` | Defining an `AuthenticationEntryPoint` `@Bean` |
| `JwtTokenParser` | Defining a `TokenParser` `@Bean` |
| `KhezyJwtFilter` | Defining a `OncePerRequestFilter` `@Bean` |
| `KhezyMethodSecurityExpressionHandler` | Defining a `MethodSecurityExpressionHandler` `@Bean` |

## How It Works

### Architecture

```
Request → CustomTokenExtractor (X-Auth-Token or Bearer)
       → JwtTokenParser (auto-configured)
       → CustomFactorExtractor (reads "mfa_claims")
       → KhezyJwtFilter (auto-configured, uses your custom beans)
       → CustomSecurityFilterChain (your custom chain)
           → FactorAppendingConfigurer
           → CustomAccessDeniedHandler (403)
           → CustomAuthenticationEntryPoint (401)
           → RequiredFactorAuthorityAuthorization (on /secure/** only)
       → SecureController
```

### Custom Components

| Component | What It Does |
|-----------|-------------|
| `CustomTokenExtractor` | Reads token from `X-Auth-Token` header first, falls back to `Authorization: Bearer` |
| `CustomFactorExtractor` | Reads MFA factors from `mfa_claims` claim instead of default `factors` |
| `CustomAccessDeniedHandler` | Returns `{"error": "access_denied", "message": "...", "path": "...", "timestamp": "..."}` |
| `CustomAuthenticationEntryPoint` | Returns `{"error": "unauthorized", "message": "..."}` |
| `CustomSecurityConfig` | Custom `SecurityFilterChain` with selective MFA on `/secure/**` only |

### Configuration

```properties
khezy.api.security.jwt.secret=my-super-secret-key
khezy.api.security.permit-patterns[0]=/auth/**
```

The `permit-patterns` property is defined but only used if you reference `KhezySecurityProperties` in your custom chain. In the custom chain here, we hardcode `/auth/**` permit.

## Running the Example

### 1. Build and Run

```bash
./gradlew -p examples/security/security-customization bootRun
```

### 2. Test Custom Token Extraction

```bash
# Get a token with mfa_claims (custom claim name)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","mfa_claims":["password","webauthn"]}' \
  | jq -r '.token')

# Access via custom X-Auth-Token header → 200
curl -s http://localhost:8080/secure \
  -H "X-Auth-Token: $TOKEN"

# Access via Bearer fallback → 200
curl -s http://localhost:8080/secure \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Test Custom Error Formats

```bash
# No token → custom 401
curl -s http://localhost:8080/secure
# {"error": "unauthorized", "message": "Authentication required"}

# Token with password only → custom 403
PW_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","mfa_claims":["password"]}' \
  | jq -r '.token')
curl -s http://localhost:8080/secure \
  -H "X-Auth-Token: $PW_TOKEN"
# {"error": "access_denied", "message": "Insufficient permissions", "path": "/secure", "timestamp": "..."}
```

### 4. Test Custom Factor Claim Name

```bash
# Token with default "factors" claim → 403 (custom extractor ignores it)
WRONG_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user","factors":["password","webauthn"]}' \
  | jq -r '.token')
curl -s http://localhost:8080/secure \
  -H "X-Auth-Token: $WRONG_TOKEN"
# 403 — custom extractor only reads "mfa_claims"
```

## What the Library Handles vs What You Implement

### Auto-Configured (still active)
- `JwtTokenParser` — parses and validates JWT tokens
- `KhezyJwtFilter` — orchestrates the authentication pipeline (uses YOUR custom beans)
- `FactorAppendingConfigurer` — appends factor authorities during authentication
- `RequiredFactorAuthorityAuthorization` — MFA authorization manager
- `AuthenticationBuilderManager` — token reconstruction

### Your Code (overrides)
- `CustomTokenExtractor` — reads from `X-Auth-Token` header
- `CustomFactorExtractor` — reads from `mfa_claims` claim
- `CustomAccessDeniedHandler` — custom 403 format
- `CustomAuthenticationEntryPoint` — custom 401 format
- `CustomSecurityFilterChain` — full chain with selective MFA paths

## How to Adapt for Your Project

1. **Pick what to override** — you don't need to override everything. Override only the components you need and let the library handle the rest.
2. **Custom token extraction** — useful for API key schemes, custom header names, or multi-source extraction
3. **Custom factor extraction** — useful when your token format uses different claim names or factor sources
4. **Custom error responses** — useful when your API contract requires a specific error format that differs from RFC 7807

What NOT to do:
- Do NOT duplicate the entire default config — override only what you need
- Do NOT forget to apply `FactorAppendingConfigurer` if you override `SecurityFilterChain` and need MFA
- Do NOT lose the `@ConditionalOnMissingBean` benefit by overriding beans you don't need to change

## Reference

- [Spring Security — SecurityFilterChain](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html)
- [Spring Boot — Auto-Configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)
- [KHEZY — Auto-configuration classes](https://github.com/khezy-app/khezy-boot)
