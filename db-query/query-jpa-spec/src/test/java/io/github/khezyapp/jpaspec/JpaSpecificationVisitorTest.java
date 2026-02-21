package io.github.khezyapp.jpaspec;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.JoinType;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class JpaSpecificationVisitorTest {
    private Root<User> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private JpaSpecificationVisitor<User> visitor;

    @BeforeEach
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
        visitor = new JpaSpecificationVisitor<>(root, query, cb);
    }

    @Test
    @DisplayName("Test basic equality comparison with path and literal")
    void testBinaryComparisonEquality() {
        final var left = new PathOperand(List.of("name"), "name");
        final var right = new LiteralOperand("John");
        final var spec = new BinaryComparisonSpec(left, ComparisonOperator.EQ, right, JoinType.INNER);

        final var mockPath = mock(Path.class);
        final var mockPredicate = mock(Predicate.class);

        doReturn(mockPath).when(root).get("name");
        doReturn(mockPredicate).when(cb).equal(any(), eq("John"));

        final var result = spec.accept(visitor);

        assertEquals(mockPredicate, result);
    }

    @Test
    @DisplayName("Test logical AND expression with multiple children to trigger cb.and")
    void testLogicalAndExpressionWithMultipleChildren() {
        final var spec1 = new BinaryComparisonSpec(new PathOperand(List.of("active"), "active"),
                ComparisonOperator.EQ, new LiteralOperand(true), JoinType.INNER);
        final var spec2 = new BinaryComparisonSpec(new PathOperand(List.of("deleted"), "deleted"),
                ComparisonOperator.EQ, new LiteralOperand(false), JoinType.INNER);

        final var mockPath1 = mock(Path.class);
        final var mockPath2 = mock(Path.class);
        final var pred1 = mock(Predicate.class);
        final var pred2 = mock(Predicate.class);
        final var expectedAnd = mock(Predicate.class);

        // Setup for first child
        doReturn(mockPath1).when(root).get("active");
        doReturn(pred1).when(cb).equal(eq(mockPath1), eq(true));

        // Setup for second child
        doReturn(mockPath2).when(root).get("deleted");
        doReturn(pred2).when(cb).equal(eq(mockPath2), eq(false));

        // Setup for the AND result
        // Note: use anyList() or argumentCaptor to match the list of predicates
        doReturn(expectedAnd).when(cb).and(Collections.singletonList(any()));

        final var andSpec = new LogicalAndSpec(List.of(spec1, spec2));
        final var result = andSpec.accept(visitor);

        assertEquals(expectedAnd, result, "Should return the combined AND predicate for multiple children");
    }

    @Test
    @DisplayName("Test logical AND expression with single child (Shortcut logic)")
    void testLogicalAndExpressionSingleChild() {
        final var spec = new BinaryComparisonSpec(new PathOperand(List.of("active"), "active"),
                ComparisonOperator.EQ, new LiteralOperand(true), JoinType.INNER);

        final var mockPath = mock(Path.class);
        final var pred = mock(Predicate.class);

        doReturn(mockPath).when(root).get("active");
        doReturn(pred).when(cb).equal(eq(mockPath), eq(true));

        final var andSpec = new LogicalAndSpec(List.of(spec));
        final var result = andSpec.accept(visitor);

        assertEquals(pred, result, "Should return child predicate directly when only one child exists");
        verify(cb, never()).and(anyList());
    }

    @Test
    @DisplayName("Test complex path with joins (fixing Set return type for getJoins)")
    void testNestedPathWithJoins() {
        final var path = new PathOperand(List.of("address", "city"), "address.city");
        final var spec = new BinaryComparisonSpec(path, ComparisonOperator.EQ, new LiteralOperand("NY"), JoinType.LEFT);

        final var addressJoin = mock(Join.class);
        final var cityPath = mock(Path.class);

        // getJoins() returns a Set, not a List
        doReturn(Set.of()).when(root).getJoins();
        doReturn(addressJoin).when(root).join("address", jakarta.persistence.criteria.JoinType.LEFT);
        doReturn(cityPath).when(addressJoin).get("city");

        spec.accept(visitor);

        verify(root).join("address", jakarta.persistence.criteria.JoinType.LEFT);
    }

    @Test
    @DisplayName("Test Full QuerySpec (using real objects for sealed interface children)")
    void testFullQuerySpec() {
        final var where = new UnaryComparisonSpec(
                new PathOperand(List.of("id"), "id"),
                ComparisonOperator.IS_NOT_NULL,
                JoinType.INNER
        );
        final var having = new BinaryComparisonSpec(
                new AggregateOperand(AggregateFunction.COUNT, new PathOperand(List.of("*"), "*")),
                ComparisonOperator.GT, new LiteralOperand(1L), JoinType.INNER);

        final var groupBy = new GroupBy(List.of(new PathOperand(List.of("dept"), "dept")));
        final var querySpec = new QuerySpec(where, having, groupBy);

        final var mockPath = mock(Path.class);
        final var mockCount = mock(Expression.class);
        final var mockWherePredicate = mock(Predicate.class);
        final var mockHavingPredicate = mock(Predicate.class);

        // Stubbing the path resolution
        doReturn(mockPath).when(root).get(anyString());

        // Stubbing the WHERE part (isNotNull)
        doReturn(mockWherePredicate).when(cb).isNotNull(any(Expression.class));

        // Stubbing the HAVING part (count and comparison)
        doReturn(mockCount).when(cb).count(any());
        final var mockCountAs = mock(Expression.class);
        doReturn(mockCountAs).when(mockCount).as(any());
        doReturn(mockHavingPredicate).when(cb).greaterThan(eq(mockCountAs), any(Comparable.class));

        final var result = visitor.visitQuerySpec(querySpec);

        // Now result will be mockWherePredicate instead of null
        assertNotNull(result, "The WHERE predicate should not be null");
        assertEquals(mockWherePredicate, result);

        verify(query).groupBy(anyList());
        verify(query).having(mockHavingPredicate);
    }

    @Test
    @DisplayName("Test Aggregate COUNT (fixing NullPointerException on .as() call)")
    void testAggregateCountFunction() {
        final var agg = new AggregateOperand(AggregateFunction.COUNT, new PathOperand(List.of("id"), "id"));
        final var spec = new BinaryComparisonSpec(agg, ComparisonOperator.GT, new LiteralOperand(5L), JoinType.INNER);

        final var mockPath = mock(Path.class);
        final var mockCount = mock(Expression.class);
        final var mockComparable = mock(Expression.class);

        doReturn(mockPath).when(root).get("id");
        doReturn(mockCount).when(cb).count(mockPath);
        // Fix for: Cannot invoke "Expression.as(...)" because "expression" is null
        doReturn(mockComparable).when(mockCount).as(Comparable.class);

        spec.accept(visitor);

        verify(cb).greaterThan(eq(mockComparable), any(Comparable.class));
    }
}
