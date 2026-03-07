# Khezy API Exception Library

A lightweight, production-ready exception handling starter for Spring Boot 3+ applications. 
This library provides a standardized way to handle global exceptions by extending 
the **RFC 7807 (Problem Details for HTTP APIs)** specification via Spring's `ProblemDetail` class.

---

## Summary
The `api-exception` library eliminates boilerplate code in your `@RestControllerAdvice`. It provides 
pre-configured handlers for common Spring, Security, and Validation exceptions while offering a fluent API 
for custom business exceptions.

## ✨ Key Features
* **Standardized Responses**: All errors follow the `ProblemDetail` format.
* **I18n Ready**: Built-in support for localized error messages.
* **Traceability**: Automatic integration with MDC (Mapped Diagnostic Context) to include `traceId` and `spanId` in error responses.
* **Developer Context**: Optional `DeveloperErrorMessage` for non-production debugging (stack traces, hints).
* **Smart Logging**: Configurable thresholds to distinguish between `WARN` (client errors) and `ERROR` (server failures).

---

## 🛠️ Choosing Your Integration

You can use this project in two ways depending on how much control you want over your Spring Context.

### 1. Using the Starter (Recommended)

The **Starter** is designed for a "plug-and-play" experience. It uses Spring Boot's Auto-configuration to automatically 
register all handlers, loggers, and message sources.

**Dependency:**

### Maven

```xml
<dependency>
    <groupId>io.github.khezyapp</groupId>
    <artifactId>api-exception-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.khezyapp:api-exception-spring-boot-starter:1.0.0'
}
```

* Result: All `@RestControllerAdvice` beans and the `DefaultErrorLogger` are automatically loaded. No extra code is required.

### 2. Using the Library (Manual Configuration)

If you prefer not to use auto-configuration, or if you want to manually pick which exception handlers to enable, 
use the Library module directly.

### Maven

```xml
<dependency>
    <groupId>io.github.khezyapp</groupId>
    <artifactId>api-exception</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.khezyapp:api-exception:1.0.0'
}
```

### Manual Setup:

If you are using `io.github.khezyapp:api-exception` without the starter, the `@RestControllerAdvice` classes will not 
be automatically detected. You must register them as beans in your own `@Configuration` class.

#### 1. Define the Required Infrastructure

The handlers require a `MessageSource` (for i18n) and an `ErrorLogger`.
```java
@Configuration
@EnableConfigurationProperties(ErrorLoggingProperties.class)
public class MyManualExceptionConfig {

    @Bean(name = "khezyI18nException")
    public MessageSource khezyMessageSource(@Value("${spring.messages.basename:messages}") String userBasenames) {
        final var messageSource = new ReloadableResourceBundleMessageSource();

        final var combinedBaseNames = userBasenames + "," + "classpath:i18n/errors/KhezyValidationMessages";
        messageSource.setBasenames(combinedBaseNames.split(","));
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setFallbackToSystemLocale(true);
        return messageSource;
    }

    @Bean
    public ErrorLogger errorLogger(ErrorLoggingProperties properties) {
        return new DefaultErrorLogger(properties);
    }
}
```

#### 2. Register the Specific Handlers

Now, you can choose which handlers from the library you want to enable by declaring them as beans:
```java
@Configuration
public class MyHandlerConfig {

    @Bean
    public CommonExceptionAdviceController commonAdvice(
            @Qualifier("khezyI18nException") MessageSource ms, 
            ErrorLogger logger) {
        return new CommonExceptionAdviceController(ms, logger);
    }

    // Optional: Only if using JJWT
    @Bean
    public JJwtExceptionAdviceController jwtAdvice(
            @Qualifier("khezyI18nException") MessageSource ms, 
            ErrorLogger logger) {
        return new JJwtExceptionAdviceController(ms, logger);
    }

    // Optional: Only if using Spring Security
    @Bean
    public AuthExceptionAdviceController jwtAdvice(
            @Qualifier("khezyI18nException") MessageSource ms,
            ErrorLogger logger) {
        return new AuthExceptionAdviceController(ms, logger);
    }
}
```

---

## ⚙️ Configuration

You can customize the logging behavior using the `khezy.error-handling` prefix in your 
`application.yml` or `application.properties`.

```yaml
khezy:
  error-handling:
    logging:
      enabled: true               # Enable/disable automatic logging
      error-threshold: 500        # Status >= 500 logged as ERROR, others as WARN
      include-stack-trace: true   # Include full stack trace in logs for ERRORs
```

## 🛠️ Custom Logging Logic

If the DefaultErrorLogger doesn't fit your needs (e.g., you want to send errors to Sentry, ELK, or Slack), 
simply implement the `ErrorLogger` interface and register it as a `@Bean`.

```java
@Component
public class CustomErrorLogger implements ErrorLogger {
    @Override
    public void log(Exception ex, ErrorResponse errorResponse) {
        // Your custom logic here
        System.out.println("Sending to external monitor: " + errorResponse.getErrorCode());
    }
}
```

## Usage Examples

### 1. Using Built-in Exceptions

The library provides several ready-to-use exceptions for common scenarios:
```java
// Throwing a 404
throw ResourceNotFoundException.resourceNotFoundBuilder()
        .message("user.not.found") // Message bundle key
        .args(new Object[]{userId})
        .build();

// Throwing a 409 Conflict
throw ConflictException.conflictBuilder()
        .message("email.already.exists")
        .build();
```

### 2. Standard Business Exception

For other scenarios, use the base `RestApiException`:

```java
throw RestApiException.builder()
        .message("insufficient.funds")
        .httpStatus(HttpStatus.PAYMENT_REQUIRED)
        .errorCode("BALANCE_TOO_LOW")
        .developerErrorMessage(DeveloperErrorMessage.builder()
                .message("User attempted to withdraw $100 with $20 balance")
                .build())
        .build();
```

## Guide: When to Extend New Exceptions

While `RestApiException` is flexible, you should create a new subclass when:

* **Reusability**: The error occurs in multiple parts of the system (e.g., `UnauthorizedException`).
* **Semantic Clarity**: It makes the code more readable (e.g., accountService.verify() throwing `AccountLockedException` 
instead of a generic one).
* **Fixed Metadata**: You want to enforce a specific `HttpStatus` or `Title` consistently.

Example Pattern:

```java
public class MyBusinessException extends RestApiException {
    private static final String TITLE = "Special Business Error";
    
    public MyBusinessException(String message) {
        super(message, TITLE, HttpStatus.UNPROCESSABLE_ENTITY, null, "MY_CODE", null, null, null);
    }
}
```

## Creating Custom Exceptions with Lombok

If you want to create a new exception class and use Lombok's `@Builder`, you must follow these rules to ensure 
it works correctly with the inheritance from `RestApiException`:

* **Annotate the Constructor**: Place the `@Builder` annotation on the constructor, not the class.
* **Unique Builder Name**: Use `builderMethodName` to give your builder a unique name (e.g., `conflictBuilder`). 
This prevents "clashing" with the `builder()` method of the base class.
* **Call Super**: Ensure your constructor passes the specific `HttpStatus` and `Title` to the super constructor.

Example Implementation:
```java
public class MyCustomException extends RestApiException {
    private static final String TITLE = "Business Constraint Violated";
    private static final HttpStatus STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    @Builder(builderMethodName = "myCustomBuilder")
    public MyCustomException(final String message,
                             final Throwable cause,
                             final Object[] args,
                             final Map<String, Object> properties,
                             final DeveloperErrorMessage developerErrorMessage) {
        // Pass the fixed Title and Status to the parent RestApiException
        super(message, TITLE, STATUS, cause, "MY_CUSTOM_ERROR_CODE", 
              args, properties, developerErrorMessage);
    }
}
```

### Usage in Service:

```java
throw MyCustomException.myCustomBuilder()
        .message("error.business.rule.violated")
        .args(new Object[] { "Parameter A" })
        .build();
```

### 🔍 Why use a specific builderMethodName?

Because `RestApiException` already defines a method named `builder()`, Lombok will encounter a naming conflict 
if you try to generate a second builder() method in your subclass. By naming it `myCustomBuilder()`, you keep the 
API clean and expressive while maintaining full compatibility with the library's core logic.

## Response Structure

Example of a `MethodArgumentNotValidException` (400 Bad Request) handled by the library:
```json
{
  "type": "about:blank",
  "title": "Invalid Input",
  "status": 400,
  "detail": "Validation failed for 1 fields",
  "instance": "/api/v1/users",
  "errorCode": "INPUT_VALIDATION_ERROR",
  "timestamp": "2026-03-07T01:46:18Z",
  "traceId": "a1b2c3d4e5f6",
  "errors": [
    {
      "field": "email",
      "reason": "must be a well-formed email address",
      "rejectedValue": "invalid-email",
      "objectName": "userDTO",
      "code": "Email"
    }
  ]
}
```

---

## 🌍 Internationalization (i18n) & Message Overriding

The library features a cascading message resolution strategy. It automatically combines your application's primary 
message bundles with the library's default error messages.

### How it works:

The internal `MessageSource` (qualified as `khezyI18nException`) reads your `spring.messages.basename` configuration 
(defaulting to `messages`). It then appends the library's internal bundle 
(`classpath:i18n/errors/KhezyValidationMessages`) to the end of the list.

> This means you can override any library default message by simply defining the same key in your own 
> `messages.properties` file. Default Error Keys

You can override these keys in your `src/main/resources/messages.properties` to customize the wording:
```properties
# JSON & Parsing Errors
io.github.khezyapp.api.exception.parse_json_error=The request body contains invalid character.
io.github.khezyapp.api.exception.invalid_json_format=JSON object contains invalid format. Value ''{0}'' is not valid for type ''{1}''.
io.github.khezyapp.api.exception.missing_request_body=The request body is missing.

# Server Errors
io.github.khezyapp.api.exception.internal_server_error=Internal server error.
io.github.khezyapp.api.exception.validation_error=The request was understood, but it contains {0} validation error(s).

# Security & JWT Errors
io.github.khezyapp.api.exception.unauthorized_error=Unauthorized access
io.github.khezyapp.api.exception.forbidden_error=Access is forbidden
io.github.khezyapp.api.exception.token_expired=Authentication token has expired
```

---

## Understanding the Response Fields

The `ErrorResponse` extends `ProblemDetail` implements the RFC 7807 (Problem Details for HTTP APIs) standard. This ensures that while 
the base fields are standardized, additional diagnostic and business metadata are flattened into the root of the 
JSON object for easy consumption.

### 1. Core RFC 7807 Fields (Standard)

* **title**: A short, human-readable summary of the problem type (e.g., "Invalid Input").
* **status**: The HTTP status code generated by the origin server for this occurrence.
* **detail**: A human-readable explanation specific to this occurrence (e.g., "The request body is missing."). 
This is the primary message for the end-user.
* **instance**: A URI reference that identifies the specific occurrence of the problem (usually the API endpoint path).

### 2. Flattened Metadata (via setProperty)

These fields are stored in the properties map of the `ProblemDetail` class but are flattened into the root JSON object during serialization:

- **traceId / spanId**: Distributed tracing identifiers (from MDC) used to correlate API errors with backend logs.
- **timestamp**: The exact ISO-8601 UTC moment the error was captured.

### 3. Khezy Library Extensions

These fields provide the structure needed for complex client-side handling:

| Field       | 	Description          | 	Usage Guidance                                                                                                                                                                       |
|-------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `errorCode` | 	Programmatic Code    | 	A stable, uppercase string (e.g., INPUT_VALIDATION_ERROR). Frontend logic should use this to trigger specific UI flows (like redirecting to login on TOKEN_EXPIRED).                 |
| `errors`    | 	Validation List      | 	A list of FieldErrorResponse objects. Use these to highlight specific input fields in a form with their respective reason and code.                                                  |
| `developer` | 	Contextual Debugging | 	Contains a message and a details map. This is purely for internal troubleshooting and helps developers understand the why behind a failure without digging through logs immediately. |

---

## 🛡️ Security & Field Usage

* **UI Display**: Use the detail field for global alerts and the errors[].reason for field-level labels.
* **Developer Context**: The developer.details map is highly flexible. It can store non-sensitive internal 
state that helped trigger the error.
* **Safe Flattening**: Because Spring's ProblemDetail flattens the properties map, ensure you do not use property 
keys that conflict with the standard fields (like `status` or `title`). The library handles this automatically for 
`traceId`, `spanId`, and `timestamp`.