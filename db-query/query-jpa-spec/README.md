# query-jpa-spec

A high-level abstraction for **Spring Data JPA** that bridges the gap between dynamic user-defined filters and 
the **JPA Criteria API**. This library allows you to transform complex query strings or programmatic builders 
into type-safe `Specification` objects automatically.

---

## Introduction

`query-jpa-spec` is part of the KHEZY initiative to simplify backend development. It enables developers 
to implement powerful, dynamic search functionality with minimal code. By leveraging an **Abstract Syntax Tree (AST)**, 
it converts input logic into JPA Predicates, handling the complexity of the Criteria API behind the scenes.

---

## Key Advantages

Unlike manual SQL or jOOQ implementations, this JPA-focused library leverages the Power of the Entity Model:
* **Automatic Join Management**: The library inspects your Entity relationships. If you filter by `author.name`, 
it automatically performs the necessary joins. It also prevents duplicate joins by checking the existing `Root` or `Join` nodes.
* **No Manual Base Query**: Since it integrates directly with `JpaSpecificationExecutor`, you don't need to 
manage the `SELECT` or `FROM` statements—Spring Data handles the execution lifecycle.
* **Type Safety**: It automatically casts expressions to `Comparable` to satisfy Criteria API requirements, 
ensuring your `GT` (>) or `LT` (<) operations are type-safe.

---

## Installation

Add the library to your project using the following coordinates:

### Maven
```xml
<dependency>
    <groupId>io.github.khezyapp</groupId>
    <artifactId>query-jpa-spec</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
dependencies {
    implementation 'io.github.khezyapp:query-jpa-spec:1.0.0'
}
```

---

## Usage

You can implement dynamic filtering using either a Raw Query String for end-user flexibility or 
the AST Builder for structured server-side control.

### 1. Using Raw Query Syntax

Perfect for generic search endpoints where the frontend sends a query string like: `title ILIKE '%Spring%' AND price < 50`.

```java
@GetMapping
public ResponseEntity<Page<Book>> getBooks(@RequestParam("q") String q) {
    var pageable = PageRequest.of(0, 10, Sort.Direction.DESC, "id");
    
    // Wraps the query string and pagination into a single helper
    var filter = new JpaPaginationQuery<Book>(q, pageable);
    
    // Pass directly to the repository
    return ResponseEntity.ok(bookRepository.findAll(
            filter.getSpecification(), 
            filter.getPageable()
    ));
}
```

### 2. Using the AST Builder

Use the builder when you want to construct the query logic based on specific, optional request parameters.

```java
@GetMapping("/builder")
public ResponseEntity<Page<Book>> getWithBuilder(@RequestParam(required = false) Long id) {
    var querySpec = ASTSpecs.builder()
            .where(ASTSpecConditions.eq("id", id))
            .build();

    var filter = new JpaPaginationQuery<Book>(querySpec, pageable);
    return ResponseEntity.ok(bookRepository.findAll(filter.getSpecification(), filter.getPageable()));
}
```

## Repository Setup

To use this library, your Spring Data Repository must extend `JpaSpecificationExecutor`.

```java
@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {
}
```

---

## Query Syntax Cheat Sheet

| Feature	     | Query String Example                      |
|--------------|-------------------------------------------|
| Comparison   | 	price > 50                               |
| String Match | 	title = 'Java' OR title ILIKE '%Spring%' |
| Nested Path  | 	category.name = 'Technology' (Auto-Join) |
| In-List      | 	id IN (1, 2, 3)                          |
| Logical      | 	(status = 'ACTIVE' AND stock > 0)        |
| Aggregates   | 	COUNT(id) > 5 GROUP BY category.id       |