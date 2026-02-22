package io.github.khezyapp.jooqspec;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FilterJooqVisitorTest {

    private FilterJooqVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new FilterJooqVisitor();
    }

    @Test
    @DisplayName("Test basic equality comparison between field and literal")
    void testBinaryComparisonEquality() {
        final var left = new PathOperand(List.of("users", "name"), "users.name");
        final var right = new LiteralOperand("John");
        final var spec = new BinaryComparisonSpec(left, ComparisonOperator.EQ, right, JoinType.INNER);

        final var result = spec.accept(visitor);

        assertNotNull(result);
        assertEquals("\"users\".\"name\" = 'John'", result.condition().toString());
    }

    @Test
    @DisplayName("Test Logical AND with multiple conditions")
    void testLogicalAndExpression() {
        final var leftSpec = new BinaryComparisonSpec(
                new PathOperand(List.of("age"), "age"),
                ComparisonOperator.GTE,
                new LiteralOperand(18L),
                JoinType.INNER
        );
        final var rightSpec = new BinaryComparisonSpec(
                new PathOperand(List.of("status"), "status"),
                ComparisonOperator.EQ,
                new LiteralOperand("ACTIVE"),
                JoinType.INNER
        );

        final var andSpec = new LogicalAndSpec(List.of(leftSpec, rightSpec));
        final var result = andSpec.accept(visitor);

        assertTrue(result.condition().toString().contains("\"age\" >= 18"));
        assertTrue(result.condition().toString().contains("and \"status\" = 'ACTIVE'"));
    }

    @Test
    @DisplayName("Test In comparison with list of values")
    void testInComparisonSpec() {
        final var left = new PathOperand(List.of("role"), "role");
        final var values = List.<Operand>of(new LiteralOperand("ADMIN"), new LiteralOperand("USER"));
        final var spec = new InComparisonSpec(left, values, JoinType.INNER);

        final var result = spec.accept(visitor);

        final var cleanedResult = result.condition().toString()
                .replace("\n", "")
                .replaceAll("\\(\s+'", "('");
        assertEquals("\"role\" in ('ADMIN', 'USER')", cleanedResult);
    }

    @Test
    @DisplayName("Test Between comparison for range")
    void testBetweenComparisonSpec() {
        final var left = new PathOperand(List.of("price"), "price");
        final var spec = new BetweenComparisonSpec(
                left,
                new LiteralOperand(100),
                new LiteralOperand(500),
                JoinType.INNER
        );

        final var result = spec.accept(visitor);

        assertEquals("\"price\" between 100 and 500", result.condition().toString());
    }

    @Test
    @DisplayName("Test Unary IS NULL condition")
    void testUnaryIsNullComparison() {
        final var left = new PathOperand(List.of("deleted_at"), "deleted_at");
        final var spec = new UnaryComparisonSpec(left, ComparisonOperator.IS_NULL, JoinType.INNER);

        final var result = spec.accept(visitor);

        assertEquals("\"deleted_at\" is null", result.condition().toString());
    }

    @Test
    @DisplayName("Test Aggregate function resolution in condition with Dialect")
    void testAggregateCountComparison() {

        final var agg = new AggregateOperand(AggregateFunction.COUNT, new PathOperand(List.of("id"), "id"));
        final var spec = new BinaryComparisonSpec(agg, ComparisonOperator.GT, new LiteralOperand(10L), JoinType.INNER);

        final var result = spec.accept(visitor);

        final var rendered = result.condition().toString();

        assertEquals("count(\"id\") > 10", rendered);
    }

    @Test
    @DisplayName("Test Full QuerySpec resolution with Dialect Context")
    void testFullQuerySpecResolution() {

        final var where = new BinaryComparisonSpec(
                new PathOperand(List.of("type"), "type"),
                ComparisonOperator.EQ,
                new LiteralOperand("SALE"),
                JoinType.INNER
        );
        final var groupBy = new GroupBy(List.of(new PathOperand(List.of("category"), "category")));
        final var having = new BinaryComparisonSpec(
                new AggregateOperand(AggregateFunction.SUM, new PathOperand(List.of("amount"), "amount")),
                ComparisonOperator.GT,
                new LiteralOperand(1000),
                JoinType.INNER
        );

        final var querySpec = new QuerySpec(where, having, groupBy);
        final var result = (JooqSpecification) querySpec.accept(visitor);

        assertEquals("\"type\" = 'SALE'", result.where().toString());
        assertEquals("sum(cast(\"amount\" as numeric)) > 1000", result.having().toString());
        assertEquals(1, result.groupBy().size());
        assertEquals("\"category\"", result.groupBy().get(0).toString());
    }

    @Test
    @DisplayName("Test Logical OR with single child returns child directly")
    void testLogicalOrSingleChild() {
        final var child = new BinaryComparisonSpec(
                new PathOperand(List.of("id"), "id"),
                ComparisonOperator.EQ,
                new LiteralOperand(1),
                JoinType.INNER
        );
        final var orSpec = new LogicalOrSpec(List.of(child));

        final var result = orSpec.accept(visitor);

        assertEquals("\"id\" = 1", result.condition().toString());
    }

    @Test
    @DisplayName("Test empty logical spec returns noCondition")
    void testEmptyLogicalAndReturnsNoCondition() {
        final var andSpec = new LogicalAndSpec(Collections.emptyList());
        final var result = andSpec.accept(visitor);

        assertEquals("true", result.condition().toString()); // jOOQ default for noCondition
    }
}
