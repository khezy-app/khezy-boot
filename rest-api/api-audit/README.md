# Introduction

`api-audit` is a lightweight, plug-and-play Spring Boot library designed to simplify the implementation of comprehensive 
audit trails. It provides a dual-layer auditing strategy:

* **Request Auditing**: Captures high-level API interactions, metadata, and request bodies.
* **Entity-Level Auditing**: Captures fine-grained data mutations (Create, Update, Delete) at the database level.

By integrating seamlessly with Spring **AOP** and **Hibernate**, `api-audit` allows developers to track 
"**who did what and when**" without cluttering business logic.

---

## The Problem & The Solution

### The Problem
* **Boilerplate Overload:** Manually writing audit logs in every controller or service is error-prone and repetitive.
* **Complex Diffing:** Calculating exactly which fields changed in a nested object or collection is technically challenging.
* **Sensitive Data Exposure:** Auditing often accidentally leaks passwords, PII, or secrets into log files.
* **Enforced Storage:** Many libraries force a specific database schema or table structure, making it hard to integrate 
with existing logging infrastructures (Elasticsearch, Mongo, or custom SQL schemas).

### The Solution
* **Automated Interception:** Uses AOP and Hibernate Interceptors to capture events transparently.
* **Javers Integration:** Leverages [Javers](https://javers.org/) to compute complex object differences 
while maintaining high performance.
* **Flattened Property Path:** Instead of deep nested objects, changes are represented using a 
**flattened path concept** (e.g., `address.city` or `contacts[0].value`), making it easy to compare and index property-by-property.
* **Storage Agnostic:** The library does not enforce a storage layer. Instead, it provides a clean interface 
(`AuditLogService`) where the developer decides exactly where the data goes—whether it's a relational database, 
a NoSQL store, or a message broker like Kafka.
* **Built-in Privacy:** Robust masking and ignoring mechanisms ensure sensitive data is either obfuscated or excluded before processing.

---

## Installation

Add the following dependency to your build.gradle (for Gradle) or pom.xml (for Maven):

```xml
<dependency>
    <groupId>io.github.khezyapp</groupId>
    <artifactId>api-audit</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'io.github.khezyapp:api-audit:1.0.0'
}
```

---

## Configuration

Enable the audit features in your `application.yml`:
```yaml
khezy:
  audit:
    enabled-audit-request: true        # Enable @AuditLog interception
    enabled-audit-entity-changes: true  # Enable Hibernate entity diffing
```

---

## Usage

### 1. Request Auditing

Annotate any method (typically in a `@RestController`) to capture the request details.
```java
@PostMapping("/{id}")
@AuditLog(action = "UPDATE_USER", entityId = "#id")
public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody UserRequest request) {
    // Business logic
    return ResponseEntity.ok(service.update(id, request));
}
```

- SpEL Support: You can use Spring Expression Language in entityId to access method arguments (`#id`) or even the 
return value (`#result.id`).

### 2. Entity Auditing

Simply ensure your JPA entities are correctly annotated. The library will automatically detect `@Entity` classes 
and track changes to their properties, excluding relationships to maintain a clean audit trail.

#### 2.1. Manual Diffing with ObjectDiffUtils

If you need to compare objects manually outside of the standard Hibernate lifecycle—for example, comparing DTOs or 
performing audits in a non-JPA environment—you can use the `ObjectDiffUtils` component.

#### 2.2. Auto-Configuration:

`ObjectDiffUtils` bean is automatically available in your Spring context if either of the following is enabled:
```yaml
khezy:
  audit:
    enabled-audit-request: true        # Triggered by Request Audit infrastructure
    enabled-audit-entity-changes: true  # Triggered by Entity Audit infrastructure
```

If both are disabled, you must define the bean manually.

#### 2.3. Usage Example:
```java
@Service
@RequiredArgsConstructor
public class ManualAuditService {

    private final ObjectDiffUtils diffUtils;

    public void processManualUpdate(User oldUser, User newUser) {
        // Generates a list of flattened property changes (e.g., "address.city")
        List<EntityFieldChange> changes = diffUtils.compares(oldUser, newUser);
        
        changes.forEach(change -> {
            log.info("Property {} changed from {} to {}", 
                change.getProperty(), change.getFrom(), change.getTo());
        });
    }
}
```

### 3. Masking Sensitive Data

Use the `@SensitiveData` annotation on fields or getter methods to protect PII. 
The library will apply these rules during both Request and Entity auditing:

* `ignore = true`: The field is completely excluded from the audit log.
* `mask = "..."`: The field value is replaced with your specified placeholder (e.g., ********).

```java
public class User {
    private String username;

    @SensitiveData(mask = "###-##-####")
    private String ssn;

    @SensitiveData(ignore = true)
    private String password;
}
```

---

## Custom Configuration

### Custom Audit Annotations

If you have common auditing patterns, you can create a meta-annotation using `@AliasFor` to reduce repetition:
```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@AuditLog
public @interface CustomAudit {

    @AliasFor(annotation = AuditLog.class, attribute = "entityId")
    String resourceId() default "#id";

    @AliasFor(annotation = AuditLog.class, attribute = "action")
    String action() default "default-action";
}
```

### Implementing `AuditLogService` (The Storage Layer)

Since the library is storage-agnostic, you must provide an implementation of `AuditLogService` to define where
your captured data is saved. If no bean is found, the library defaults to a `NoopAuditLogService` which only logs to `DEBUG`.

**Example: Persistence via JPA/SQL**

```java
@Service
@RequiredArgsConstructor
public class DatabaseAuditLogService implements AuditLogService {

    private final AuditRequestRepository requestRepository;
    private final EntityChangeRepository changeRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onRequest(final AuditLogRecord<?> record) {
        // Map the DTO to your specific table schema
        AuditRequestEntity entity = new AuditRequestEntity();
        entity.setAction(record.getAction());
        entity.setTraceId(record.getTraceId());
        entity.setPayload(objectMapper.writeValueAsString(record.getBody()));
        requestRepository.save(entity);
    }

    @Override
    public void onAuditEntityChanges(final AuditEntityChange change) {
        // Save the flattened property changes to a different table
        AuditChangeEntity entity = new AuditChangeEntity();
        entity.setEntityName(change.getEntityName());
        entity.setEntityId(change.getEntityId().toString());
        entity.setChanges(objectMapper.writeValueAsString(change.getChanges()));
        changeRepository.save(entity);
    }
}
```

---

## SpEL Expression Cheatsheet

The `entityId` attribute in `@AuditLog` supports dynamic evaluation using Spring Expression Language (SpEL). 
This allows you to extract identifiers from method arguments, nested objects, or even the return value of the method.

### 1. Accessing Method Arguments

You can access method parameters by their name (prefixed with `#`) or their positional index.

| Syntax       | Description                               | Example               |
|--------------|-------------------------------------------|-----------------------|
| #name        | Access argument by its parameter name.    | `#id`, `#request`     |
| `#p0`, `#a0` | Access the first argument by index.       | `#p0` (same as `#id`) |
| `#p1.name`   | Access a property of the second argument. | `#p1.username`        |

### 2. Accessing the Method Result

To audit identifiers generated during method execution (like an auto-incremented ID), use the #result variable.

| Syntax       | Description                                 | Example           |
|--------------|---------------------------------------------|-------------------|
| `#result`    | The entire object returned by the method.   | `#result`         |
| `#result.id` | A specific property of the returned object. | `#result.orderNo` |

### 3. Advanced Navigation & Logic

SpEL supports safe navigation and conditional logic to prevent `NullPointerException` during evaluation.

| Feature          | Syntax                                          | Example                       |
|------------------|-------------------------------------------------|-------------------------------|
| Safe Navigation  | Avoids null errors if a property is missing.    | `#request?.user?.id`          |
| Elvis Operator   | Provides a default value if the result is null. | `#id ?: 'N/A'`                |
| Ternary Operator | Standard `if-then-else` logic.                  | `#id != null ? #id : 'GUEST'` |
| Collections      | Access elements from Maps or Lists.             | `"#p0['userId']`, `#list[0]`  |

### Common Examples

* **Simple ID**: `entityId = "#id"`
* **Nested Property**: `entityId = "#request.account.uuid"`
* **Post-Persist ID**: `entityId = "#result.id"`
* **Composite Key**: `entityId = "#type + '_' + #id"`

---

## Advanced Customization

While `api-audit` provides robust defaults, it is designed with a **Strategy-based architecture** that allows 
you to override or extend how data is captured and masked.

### 1. Custom Request Body Extraction

If your application uses a non-standard protocol or a specific binary format (like Protobuf or a custom XML structure) 
that the default `PayloadBodyExtractor` cannot parse, you can implement a custom extractor.

**Use Case**: Extracting specific metadata from a custom Header or a binary stream.
```java
@Component
public class CustomHeaderBodyExtractor extends AbstractAbstractBodyExtractor {

    public CustomHeaderBodyExtractor(SensitiveMasker sensitiveMasker) {
        super(sensitiveMasker);
    }

    @Override
    public boolean supports(MethodInvocation invocation, HttpServletRequest request) {
        // Only trigger for a specific custom content type
        return isContentType(request, MediaType.valueOf("application/x-custom-v1"));
    }

    @Override
    protected Map<String, Object> doExtract(MethodInvocation invocation, HttpServletRequest request) {
        final var params = new HashMap<String, Object>();
        // Extracting from headers instead of body
        params.put("x-trace-id", request.getHeader("X-Custom-Trace"));
        params.put("client-version", request.getHeader("X-Client-Version"));
        return params;
    }

    @Override
    public int getOrder() {
        return -10; // Higher priority than default extractors
    }
}
```

#### Available Defaults:

* `FormBodyExtractor`: Handles application/x-www-form-urlencoded.
* `MultiPartBodyExtractor`: Handles file uploads and multi-part forms.
* `PayloadBodyExtractor`: Handles JSON/XML raw body payloads.
* `CompositeBodyExtractor`: The orchestrator that manages the execution chain.

### 2. Custom Sensitive Data Masking

The `SensitiveMasker` uses a recursive strategy to find and mask data. By default, complex objects (Beans) 
are transformed into a `Map<String, Object>` during the masking process. This approach is highly efficient as 
it avoids the reflection overhead of re-populating a new class instance and prevents unintended modifications 
to your original domain objects.

#### How it Works Internally (Recursion & Circular References):

To implement a custom strategy safely and remain compatible with the library's flattening features, follow these core principles:

* **Prevent Infinite Loops**: Call `context.registerVisited(payload, proceedObject)` before processing any fields.
* **Transform to Map**: Use a `Map<String, Object>` as the result container (`proceedObject`) to store the masked properties.
* **Recursive Processing**: For nested complex objects, call `context.processMask(valueToMask)`. 
This ensures that nested beans are also converted into maps and masked correctly.

**Example: Custom Strategy for a Specific Domain Object**

```java
@Component
public class MySpecialDataStrategy implements SensitiveMaskerStrategy {

    @Override
    public boolean supports(Object payload) {
        return payload instanceof MySpecialData;
    }

    @Override
    public Object mask(Object payload, SensitiveMaskerContext context) {
        final MySpecialData source = (MySpecialData) payload;

        // 1. Create a Map to hold the result (avoids modifying the original object)
        final var proceedObject = new HashMap<String, Object>();

        // 2. Register immediately to handle circular references
        context.registerVisited(source, proceedObject);

        // 3. Populate properties
        proceedObject.put("id", source.getId());
        proceedObject.put("name", source.getName());

        // 4. Manually mask specific sensitive fields
        proceedObject.put("secretKey", "********");

        // 5. Recursively mask nested complex objects (will return a Map)
        if (source.getMetadata() != null) {
            proceedObject.put("metadata", context.processMask(source.getMetadata()));
        }

        return proceedObject;
    }
}
```

#### Why we use Maps for complex objects:

* **Zero Side Effects**: The original entity or DTO remains untouched.
* **Performance**: There is no need to find constructors or use setters via reflection to create a "masked" version of a specific class.
* **Log Compatibility**: The library’s flattening logic and JSON serialization work seamlessly with Maps to generate 
property paths like `metadata.version`.

#### Default Strategies Provided:

* `CollectionSensitiveMaskerStrategy`: Recursively masks Lists, Sets, and Arrays.
* `MapSensitiveMaskerStrategy`: Masks Map values while preserving keys.
* `BeanSensitiveMaskerStrategy`: Uses reflection to find fields annotated with `SensitiveData` in standard POJOs.

### 3. Summary of Extension Points

| If you want to change...                    | Implement / Extend                                            | How to Register                                          |
|---------------------------------------------|---------------------------------------------------------------|----------------------------------------------------------|
| How HTTP body is read,AbstractBodyExtractor | Register as a `@Bean` or via `CompositeBodyExtractorBuilder`. |
| How a specific Type is masked               | `SensitiveMaskerStrategy`                                     | Register as a `@Bean`; it will be auto-discovered.       |
| Where the logs are stored                   | `AuditLogService`                                             | Implement the interface to save to SQL, NoSQL, or Kafka. |
| How properties are resolved                 | `ValueResolver`                                               | Provide a custom `@Bean` of `ValueResolver`.             |