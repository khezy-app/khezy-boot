package io.github.khezyapp.grammar;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import io.github.khezyapp.query.FilterSpecLexer;
import io.github.khezyapp.query.FilterSpecParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ASTSpecVisitorTest {

    private QuerySpec parse(final String query) {
        final var lexer = new FilterSpecLexer(CharStreams.fromString(query));
        final var tokens = new CommonTokenStream(lexer);
        final var parser = new FilterSpecParser(tokens);
        final var visitor = new ASTSpecVisitor();
        return (QuerySpec) visitor.visit(parser.filterSpec());
    }

    @Test
    void testComplexLogicalNesting() {
        // Testing operator precedence: AND higher than OR, and Parentheses
        final var query = "(age > 21 OR status = 'VIP') AND country = 'US'";
        final var ast = parse(query);

        // Root where is always wrapped in LogicalOrSpec by the visitor
        final var rootOr = (LogicalOrSpec) ast.whereSpec();
        final var mainAnd = (LogicalAndSpec) rootOr.children().get(0);

        assertEquals(2, mainAnd.children().size());
        assertInstanceOf(LogicalOrSpec.class, mainAnd.children().get(0)); // The (age > 21 OR status = 'VIP') part
        assertInstanceOf(BinaryComparisonSpec.class, mainAnd.children().get(1)); // The country = 'US' part
    }

    @Test
    void testAggregateAndGroupBy() {
        final var query = "SUM(order.amount) > 1000 GROUP BY user.id, user.name HAVING COUNT(*) > 5";
        final var ast = parse(query);

        // Verify Group By
        final var groupBy = ast.groupBy();
        assertNotNull(groupBy);
        assertEquals(2, groupBy.items().size());
        assertEquals("user.id", ((PathOperand) groupBy.items().get(0)).path());

        // Verify Having
        final var having = (LogicalOrSpec) ast.havingSpec();
        final var havingAnd = (LogicalAndSpec) having.children().get(0);
        final var countComp = (BinaryComparisonSpec) havingAnd.children().get(0);

        final var left = (AggregateOperand) countComp.left();
        assertEquals(AggregateFunction.COUNT, left.function());
        assertEquals("*", left.path().path());
    }

    @Test
    void testJoinTypesInComparison() {
        // Testing explicit JOIN types in the grammar
        final var query = "LEFT user.id = 101 AND RIGHT profile.type != 'GUEST'";
        final var ast = parse(query);

        final var rootOr = (LogicalOrSpec) ast.whereSpec();
        final var andSpec = (LogicalAndSpec) rootOr.children().get(0);

        final var leftJoinSpec = (BinaryComparisonSpec) andSpec.children().get(0);
        final var rightJoinSpec = (BinaryComparisonSpec) andSpec.children().get(1);

        assertEquals(JoinType.LEFT, leftJoinSpec.joinType());
        assertEquals(JoinType.RIGHT, rightJoinSpec.joinType());
    }

    @Test
    void testBetweenAndInOperators() {
        final var query = "score BETWEEN 10 AND 20 AND category IN ('A', 'B', 'C')";
        final var ast = parse(query);

        final var rootOr = (LogicalOrSpec) ast.whereSpec();
        final var andSpec = (LogicalAndSpec) rootOr.children().get(0);

        // Test Between
        final var between = (BetweenComparisonSpec) andSpec.children().get(0);
        assertEquals(10L, ((LiteralOperand) between.from()).value());
        assertEquals(20L, ((LiteralOperand) between.to()).value());

        // Test In
        final var inSpec = (InComparisonSpec) andSpec.children().get(1);
        assertEquals(3, inSpec.right().size());
        assertEquals("A", ((LiteralOperand) inSpec.right().get(0)).value());
    }

    @Test
    void testNullComparisons() {
        final var query = "deleted_at IS NULL AND updated_at IS NOT NULL";
        final var ast = parse(query);

        final var rootOr = (LogicalOrSpec) ast.whereSpec();
        final var andSpec = (LogicalAndSpec) rootOr.children().get(0);

        final var isNull = (UnaryComparisonSpec) andSpec.children().get(0);
        final var isNotNull = (UnaryComparisonSpec) andSpec.children().get(1);

        assertEquals(ComparisonOperator.IS_NULL, isNull.operator());
        assertEquals(ComparisonOperator.IS_NOT_NULL, isNotNull.operator());
    }

    @Test
    void testNumericLiterals() {
        final var query = "rating = 4.5 AND version = 2";
        final var ast = parse(query);

        final var rootOr = (LogicalOrSpec) ast.whereSpec();
        final var andSpec = (LogicalAndSpec) rootOr.children().get(0);

        final var doubleVal = (BinaryComparisonSpec) andSpec.children().get(0);
        final var longVal = (BinaryComparisonSpec) andSpec.children().get(1);

        assertEquals(4.5, ((LiteralOperand) doubleVal.right()).value());
        assertEquals(2L, ((LiteralOperand) longVal.right()).value());
    }
}
