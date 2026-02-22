# Specification AST Library Usage Guide

This library allows you to convert string-based filters into JPA Criteria API Predicates using an intermediate Abstract Syntax Tree (AST).

---

## 🛠 Basic Usage

### 1. Filter a Repository
The most common use case is passing a query string directly to a Spring Data JPA repository using `FilterSpecification`.

```java
public List<User> getUsers(String filter) {
    // Input example: "name = 'John' AND age > 25"
    FilterSpecification<User> spec = new FilterSpecification<>(filter);
    return userRepository.findAll(spec);
}
```

### 2. Manual AST Construction
You can also build the AST programmatically if you need to bypass string parsing.

```java
PathOperand path = new PathOperand(List.of("status"), "status");
LiteralOperand value = new LiteralOperand("ACTIVE");

BinaryComparisonSpec spec = new BinaryComparisonSpec(
        path, 
        ComparisonOperator.EQ, 
        value, 
        JoinType.INNER
);
```

---

## 💡 Pro Tips

### 🖇️ Automatic Join Management
The `JpaSpecificationVisitor` handles nested paths (e.g., `address.city.zip`) automatically.
* It checks if a join for a specific attribute already exists in the current `Root` or `Join` node to prevent duplicate SQL joins.
* If it doesn't exist, it creates one using the `JoinType` specified in your comparison (Default is `INNER`).

### 📊 Aggregates and Grouping
You can perform complex filtering on aggregated data using the `HAVING` and `GROUP BY` support:

```sql
-- Input Query String
"COUNT(id) > 5 GROUP BY department.id"
```

### ⚡ Shortcut Logic
The logical specs (`LogicalAndSpec`, `LogicalOrSpec`) implement shortcut logic:
* If a logical group contains only **one** child, the visitor returns that child's predicate directly without wrapping it in a redundant `cb.and()` or `cb.or()` call.
* Empty logical groups return `cb.conjunction()` (a "1=1" predicate).

### 🔍 Type Safety in Comparisons
When performing comparisons like `GT` (>) or `LT` (<), the visitor automatically casts expressions to `Comparable` to satisfy the Criteria API requirements. Ensure your `LiteralOperand` values match the underlying entity field types (e.g., `Long` for IDs).

---

## 🧪 Testing with Mockito
When unit testing your custom visitors, ensure you mock the following JPA chain:
1.  Mock `Root.getJoins()` to return `Set.of()` to trigger join creation logic.
2.  Mock `Expression.as(Comparable.class)` when testing inequality operators (`GT`, `LT`, etc.) to avoid `NullPointerException`.