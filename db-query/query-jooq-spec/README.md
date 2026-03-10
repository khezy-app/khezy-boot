# query-jooq-spec

A lightweight Specification AST (Abstract Syntax Tree) library for **jOOQ**. This project acts as a bridge between 
dynamic user input—either through a human-readable query string or a type-safe programmatic builder—and jOOQ's powerful DSL.

---

## Introduction

`query-jooq-spec` is designed to simplify the implementation of dynamic filtering, searching, and pagination 
in Spring Boot applications using jOOQ. It allows developers to transform complex query logic into jOOQ-compatible 
`Condition`, `Group Field`, and `Having` clauses without writing repetitive boilerplate code.

---

## ⚠️ Limitations & Scope

This project follows the **Specification Pattern**.
* **Focus**: Its primary goal is to generate the `WHERE`, `GROUP BY`, and `HAVING` components of a query.
* **Base Query Management**: It **does not** automatically handle table joins or the base `SELECT` structure. 
You are responsible for ensuring all tables referenced in your specifications (e.g., `book.title`) are manually 
joined in your jOOQ statement.
* **General Use-case**: This library is optimized for standard CRUD and search operations. 
It is not intended for highly advanced SQL features or vendor-specific complex subqueries.

---

## Installation

Add the library to your project using the following coordinates:

### Maven
```xml
<dependency>
    <groupId>io.github.khezyapp</groupId>
    <artifactId>query-jooq-spec</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
dependencies {
    implementation 'io.github.khezyapp:query-jooq-spec:1.0.0'
}
```

---

## Usage

You can generate specifications in two ways: using a Raw Query String for flexible searching, or 
using the Type-safe Builder for structured requests.

### 1. Using Raw Query Syntax

Perfect for "Search" bars where users or frontend clients pass a string directly.

* **Syntax**: `book.price > 100 AND book.id = 10`

```java
@GetMapping
public ResponseEntity<Page<Book>> getAll(@RequestParam("q") String q) {
    var pageable = new JooqPageRequest.Builder()
            .pageSize(10)
            .pageNumber(0)
            .build();

    // Automatically parses the string 'q' into an AST and then to jOOQ specs
    var jooqPagination = JooqPaginationQueries.of(q, pageable);
    
    return ResponseEntity.ok(bookService.getBook(jooqPagination));
}
```

### 2. Using the AST Builder

Ideal when you have specific request parameters (like an ID, Category, or Date Range) and want to control 
the behavior programmatically.

```java
@GetMapping("/builder")
public ResponseEntity<Page<Book>> getWithBuilder(
        @RequestParam(required = false) Long id,
        @RequestParam(required = false) String title) {
    
    var querySpec = ASTSpecs.builder()
            .where(
                ASTSpecConditions.and(
                    ASTSpecConditions.ilike("book.title", title),
                    ASTSpecConditions.eq("book.id", id)
                )
            )
            .build();

    var jooqPagination = JooqPaginationQueries.of(querySpec, pageable);
    return ResponseEntity.ok(bookService.getBook(jooqPagination));
}
```

## Service Layer Integration

In your service, you simply apply the generated specification to your jOOQ DSL context.

```java
public Page<Book> getBook(JooqPaginationQuery query) {
    var spec = query.specification();

    var select = dsl.select(...)
            .from(BOOK)
            .leftJoin(AUTHOR).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID)) // Manage your joins here!
            .where(spec.where());

    // Apply Grouping if the query requires it
    if (!spec.groupBy().isEmpty()) {
        select.addGroupBy(spec.groupBy());
        select.addHaving(spec.having());
    }

    // Apply Pagination
    select.addLimit(query.getPageSize());
    select.addOffset(query.getOffset());

    return executeAndMap(select);
}
```

---

## Query Syntax Cheat Sheet

| Feature	         | Query String Example                   |
|------------------|----------------------------------------|
| Comparison       | 	book.price > 50                       |
| Strings	         | author.name = 'J.K. Rowling'           |
| Case-Insensitive | 	book.title ILIKE '%java%'             |
| In-List          | 	book.id IN (1, 2, 3)                  |
| Logical	         | price < 100 AND title = 'Spring'       |
| Aggregates       | 	COUNT(book.id) > 5 GROUP BY author.id |