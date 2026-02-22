# Specification AST Library

A lightweight, library designed to decouple business logic from common infrastructure tasks. 
This library provides a structured **Abstract Syntax Tree (AST)** for building dynamic queries, filtering, 
and specifications using a human-readable syntax powered by ANTLR.

---

## 🚀 Key Features

* **Logic First:** Focus on your business requirements instead of boilerplate configuration.
* **Structured AST:** Strongly-typed representation of queries including comparisons, logical grouping, and aggregates.
* **Visitor Pattern:** Easily transform the AST into SQL, HQL, or any other target format.
* **Fluent Builders:** Mutation-ready objects for easy manipulation of query nodes.

---

## 🛠 Usage

### 1. Parsing a Query String
Use the `ASTSpecs` utility to convert a string-based filter into an actionable object model.

```java
String query = "age > 18 AND status = 'ACTIVE'";
QuerySpec spec = ASTSpecs.fromQuery(query);
```

### 2. Traversing the AST
Implement the \`SpecificationVisitor<R>\` to translate the query into your desired output (e.g., a JPA Criteria query).

```java
public class MySqlGenerator implements SpecificationVisitor<String> { 
    @Override 
    public String visitBinaryComparisonSpec(BinaryComparisonSpec spec) {
        return spec.left().path() + " " + spec.operator().getValue() + " " + spec.right();
    }
// Implement other methods...
}
```

---

## 🏗 Architecture Components

| Component            | Description                                                                      |
|:---------------------|:---------------------------------------------------------------------------------|
| **ASTSpec**          | The sealed root interface for all tree nodes.                                    |
| **Operand**          | Represents values, paths (e.g., \`user.name\`), or aggregate functions.          |
| **Comparison Specs** | Specialized nodes for \`BETWEEN\`, \`IN\`, \`Binary\`, and \`Unary\` operations. |
| **Logical Specs**    | Containers for \`AND\` and \`OR\` logic grouping.                                |

---

## 📋 Requirements

* **Java 17+** (Uses Records and Sealed Interfaces)
* **ANTLR 4.x** Runtime

---

## 📝 License

Distributed under the MIT License. See LICENSE for more information.