# jOOQ Dynamic Filtering & Pagination Guide

This guide explains how to use the Specification AST library to implement dynamic search, filtering, 
and pagination using jOOQ.

---

## 🏗️ Architecture Overview

The library acts as a bridge between a **human-readable query string** and **jOOQ's type-safe DSL**.



1.  **Parsing**: Your query string (e.g., `book.price > 20`) is converted into an Abstract Syntax Tree (AST).
2.  **Transformation**: The `FilterJooqVisitor` visits the AST and generates jOOQ `Condition` and `Field` objects.
3.  **Execution**: The generated components are plugged directly into your jOOQ `select` statements.

---

## 🚀 Getting Started

### 1. Define your Controller
The controller captures the user's search string (`q`) and pagination preferences.

```java
@GetMapping("/books")
public ResponseEntity<Page<Book>> getAll(
        @RequestParam("q") String q, // Example: "book.title = 'Java' AND book.price < 50"
        @RequestParam(defaultValue = "10") int pageSize,
        @RequestParam(defaultValue = "0") int pageNumber
) {
    // 1. Define sorting and paging
    var pageable = new JooqPageRequest.Builder()
            .pageSize(pageSize)
            .pageNumber(pageNumber)
            .sortFields(List.of("book.id"))
            .sortDirections(List.of("ASC"))
            .build();

    // 2. Build the unified Pagination Query
    var jooqPagination = JooqPaginationQueries.of(q, pageable);

    // 3. Execute via Service
    return ResponseEntity.ok(bookService.getBook(jooqPagination));
}
```

### 2. Apply the Specification in your Service

In your service layer, extract the specification() to apply filters, groups, and having clauses.

```java
public Page<Book> getBook(JooqPaginationQuery query) {
    var spec = query.specification();

    var select = dsl.select(...)
            .from(BOOK)
            .where(spec.where()); // <--- Dynamic Filter applied here

    // Apply Grouping and Having if the query contains aggregates
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

## 📝 Query Syntax Cheat Sheet

| Feature	   | Query String Example                   |
|------------|----------------------------------------|
| Comparison | 	book.price > 50                       |
| Strings    | 	author.name = 'J.K. Rowling'          |
| In-List 	  | book.id IN (1, 2, 3)                   |
| Logical    | 	price < 100 AND title = 'Spring'      |
| Aggregates | 	COUNT(book.id) > 5 GROUP BY author.id |
| Nulls	     | author_id IS NOT NULL                  |

## 💡 Best Practices for Beginners
### 🛡️ Qualified Names

Always use Qualified Names (Table + Field) in your queries and your jOOQ definitions. 
This prevents "ambiguous column" errors when joining multiple tables.

- Bad: id
- Good: book.id

### 🖇️ Join Awareness

The library generates conditions, but it does not automatically join tables in your SQL. You must ensure that every 
table referenced in your query string (e.g., author.name) is manually joined in your Java code:

```java
dsl.select(...)
   .from(BOOK)
   .leftJoin(AUTHOR).on(BOOK_AUTHOR_ID.eq(AUTHOR_ID)) // Ensure AUTHOR is joined!
   .where(spec.where())
```
