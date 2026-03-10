package io.github.khezyapp.grammar.ast.builder;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility class providing static factory methods to create {@link ASTSpec} and {@link Operand} instances.
 * This class serves as a Domain Specific Language (DSL) to simplify the construction of
 * Abstract Syntax Tree (AST) query conditions including logical operations, comparisons,
 * and aggregate functions.
 */
public final class ASTSpecConditions {

    private ASTSpecConditions() {
    }

    /**
     * Creates a logical OR specification from the provided conditions.
     *
     * @param conditions the specifications to be joined by OR.
     * @return a {@link LogicalOrSpec} containing the non-null conditions.
     */
    public static ASTSpec or(final ASTSpec... conditions) {
        final var useConditions = Arrays.stream(conditions)
                .filter(Objects::nonNull)
                .toList();
        return new LogicalOrSpec(useConditions);
    }

    /**
     * Creates a logical AND specification from the provided conditions.
     *
     * @param conditions the specifications to be joined by AND.
     * @return a {@link LogicalAndSpec} containing the non-null conditions.
     */
    public static ASTSpec and(final ASTSpec... conditions) {
        final var useConditions = Arrays.stream(conditions)
                .filter(Objects::nonNull)
                .toList();
        return new LogicalAndSpec(useConditions);
    }

    /**
     * Creates an equality (EQ) comparison specification.
     *
     * @param left     the left-hand operand.
     * @param right    the right-hand operand.
     * @param joinType the type of join to apply (defaults to {@link JoinType#INNER} if null).
     * @return a {@link BinaryComparisonSpec} for equality.
     */
    public static ASTSpec eq(final Operand left,
                             final Operand right,
                             final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.EQ, right, usedJoinType);
    }

    public static ASTSpec eq(final Operand left,
                             final Operand right) {
        return eq(left, right, null);
    }

    public static ASTSpec eq(final String left,
                             final Object right) {
        final var leftOp = pathOperand(left);
        final var rightOp = literalOperand(right);
        return eq(leftOp, rightOp, null);
    }

    /**
     * Creates a not-equal (NE) comparison specification.
     *
     * @param left     the left-hand operand.
     * @param right    the right-hand operand.
     * @param joinType the join type.
     * @return a {@link BinaryComparisonSpec} for inequality.
     */
    public static ASTSpec ne(final Operand left,
                             final Operand right,
                             final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.NE, right, usedJoinType);
    }

    public static ASTSpec ne(final Operand left,
                             final Operand right) {
        return ne(left, right, null);
    }

    public static ASTSpec ne(final String left,
                             final Object right) {
        return ne(pathOperand(left), literalOperand(right), null);
    }

    /**
     * Creates a less-than (LT) comparison specification.
     */
    public static ASTSpec lt(final Operand left,
                             final Operand right,
                             final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.LT, right, usedJoinType);
    }

    public static ASTSpec lt(final Operand left,
                             final Operand right) {
        return lt(left, right, null);
    }

    public static ASTSpec lt(final String left,
                             final Object right) {
        return lt(pathOperand(left), literalOperand(right), null);
    }

    /**
     * Creates a less-than-or-equal (LTE) comparison specification.
     */
    public static ASTSpec lte(final Operand left,
                              final Operand right,
                              final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.LTE, right, usedJoinType);
    }

    public static ASTSpec lte(final Operand left,
                              final Operand right) {
        return lte(left, right, null);
    }

    public static ASTSpec lte(final String left,
                              final Object right) {
        return lte(pathOperand(left), literalOperand(right), null);
    }

    /**
     * Creates a greater-than (GT) comparison specification.
     */
    public static ASTSpec gt(final Operand left,
                             final Operand right,
                             final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.GT, right, usedJoinType);
    }

    public static ASTSpec gt(final Operand left,
                             final Operand right) {
        return gt(left, right, null);
    }

    public static ASTSpec gt(final String left,
                             final Object right) {
        return gt(pathOperand(left), literalOperand(right), null);
    }

    /**
     * Creates a greater-than-or-equal (GTE) comparison specification.
     */
    public static ASTSpec gte(final Operand left,
                              final Operand right,
                              final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.GTE, right, usedJoinType);
    }

    public static ASTSpec gte(final Operand left,
                              final Operand right) {
        return gte(left, right, null);
    }

    public static ASTSpec gte(final String left,
                              final Object right) {
        return gte(pathOperand(left), literalOperand(right), null);
    }

    /**
     * Static factory method that creates an {@link ASTSpec} representing a SQL-style 'LIKE' comparison.
     * <p>
     * This method validates the operands and defaults the join strategy to {@link JoinType#INNER}
     * if no specific join type is provided. If the right-hand operand (the pattern) is null,
     * the method returns null to avoid invalid comparison generation.
     * </p>
     *
     * @param left     the left-hand operand, typically a column or attribute name (must not be null)
     * @param right    the right-hand operand representing the search pattern (e.g., "%value%")
     * @param joinType the table join strategy to apply if the left operand requires a relationship path
     * @return a {@link BinaryComparisonSpec} configured with the {@link ComparisonOperator#LIKE} operator,
     * or null if the right operand is null
     * @throws NullPointerException if the {@code left} operand is null
     */
    public static ASTSpec like(final Operand left,
                               final Operand right,
                               final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.LIKE, right, usedJoinType);
    }

    public static ASTSpec like(final String left,
                               final String right) {
        final var leftOperand = pathOperand(left);
        final var rightOperand = literalOperand(right);
        return like(leftOperand, rightOperand, null);
    }

    /**
     * Static factory method that creates an {@link ASTSpec} representing a case-insensitive
     * 'ILIKE' comparison.
     * <p>
     * This method ensures the left operand is present and provides a default join strategy
     * of {@link JoinType#INNER} if none is specified. If the right-hand pattern is null,
     * it returns null to prevent generating an invalid comparison.
     * </p>
     *
     * @param left     the left-hand operand, typically representing a database field (must not be null)
     * @param right    the right-hand operand containing the pattern to match against
     * @param joinType the join strategy to use if the field path spans multiple tables
     * @return a {@link BinaryComparisonSpec} configured with the {@link ComparisonOperator#ILIKE} operator,
     * or null if the right operand is null
     * @throws NullPointerException if the {@code left} operand is null
     */
    public static ASTSpec ilike(final Operand left,
                                final Operand right,
                                final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(right)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BinaryComparisonSpec(left, ComparisonOperator.ILIKE, right, usedJoinType);
    }

    public static ASTSpec ilike(final String left,
                                final String right) {
        final var leftOperand = pathOperand(left);
        final var rightOperand = literalOperand(right);
        return ilike(leftOperand, rightOperand, null);
    }

    /**
     * Creates an IN comparison specification.
     *
     * @param left     the operand to check.
     * @param right    the list of operands representing the set of values.
     * @param joinType the join type.
     * @return an {@link InComparisonSpec}.
     */
    public static ASTSpec in(final Operand left,
                             final List<Operand> right,
                             final JoinType joinType) {
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        final var useRight = right.stream()
                .filter(Objects::nonNull)
                .toList();
        if (useRight.isEmpty()) {
            return null;
        }
        return new InComparisonSpec(left, useRight, usedJoinType);
    }

    public static ASTSpec in(final Operand left,
                             final List<Operand> right) {
        return in(left, right, null);
    }

    public static ASTSpec in(final String left,
                             final List<Object> values) {
        final var right = values.stream()
                .map(v -> v instanceof Operand op ? op : literalOperand(v))
                .toList();
        return in(pathOperand(left), right, null);
    }

    /**
     * Creates a BETWEEN comparison specification.
     *
     * @param left     the operand to check.
     * @param from     the lower bound operand.
     * @param to       the upper bound operand.
     * @param joinType the join type.
     * @return a {@link BetweenComparisonSpec}.
     */
    public static ASTSpec between(final Operand left,
                                  final Operand from,
                                  final Operand to,
                                  final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        if (Objects.isNull(from) || Objects.isNull(to)) {
            return null;
        }
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new BetweenComparisonSpec(left, from, to, usedJoinType);
    }

    public static ASTSpec between(final Operand left,
                                  final Operand from,
                                  final Operand to) {
        return between(left, from, to, null);
    }

    public static ASTSpec between(final String left,
                                  final Object from,
                                  final Object to) {
        return between(pathOperand(left), literalOperand(from), literalOperand(to), null);
    }

    /**
     * Creates an IS NULL comparison specification.
     */
    public static ASTSpec isNull(final Operand left,
                                 final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new UnaryComparisonSpec(left, ComparisonOperator.IS_NULL, usedJoinType);
    }

    public static ASTSpec isNull(final Operand left) {
        return isNull(left, null);
    }

    public static ASTSpec isNull(final String left) {
        return isNull(pathOperand(left), null);
    }

    /**
     * Creates an IS NOT NULL comparison specification.
     */
    public static ASTSpec isNotNull(final Operand left,
                                    final JoinType joinType) {
        Objects.requireNonNull(left, "left must not be null");
        final var usedJoinType = Objects.isNull(joinType) ? JoinType.INNER : joinType;
        return new UnaryComparisonSpec(left, ComparisonOperator.IS_NOT_NULL, usedJoinType);
    }

    public static ASTSpec isNotNull(final Operand left) {
        return isNotNull(left, null);
    }

    public static ASTSpec isNotNull(final String left) {
        return isNotNull(pathOperand(left), null);
    }

    /**
     * Creates a literal operand for a raw value.
     *
     * @param value the value (Object, String, etc).
     * @return a {@link LiteralOperand}, or null if the value is null or a blank string.
     */
    public static LiteralOperand literalOperand(final Object value) {
        if (Objects.isNull(value) || value instanceof String str && str.isBlank()) {
            return null;
        }
        return new LiteralOperand(value);
    }

    /**
     * Creates a path operand from a dot-separated string.
     *
     * @param path the property path.
     * @return a {@link PathOperand} split by dots.
     */
    public static PathOperand pathOperand(final String path) {
        final var usePath = Objects.isNull(path) ? "" : path;
        if (usePath.isBlank()) {
            return null;
        }
        return new PathOperand(Arrays.asList(usePath.split("\\.")), path);
    }

    /**
     * Creates a SUM aggregate operand.
     */
    public static AggregateOperand sum(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(AggregateFunction.SUM, pathOperand(path));
    }

    /**
     * Creates an AVG aggregate operand.
     */
    public static AggregateOperand avg(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(AggregateFunction.AVG, pathOperand(path));
    }

    /**
     * Creates a COUNT aggregate operand.
     */
    public static AggregateOperand count(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(AggregateFunction.COUNT, pathOperand(path));
    }

    /**
     * Creates a MIN aggregate operand.
     */
    public static AggregateOperand min(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(AggregateFunction.MIN, pathOperand(path));
    }

    /**
     * Creates a MAX aggregate operand.
     */
    public static AggregateOperand max(final String path) {
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(AggregateFunction.MAX, pathOperand(path));
    }

    /**
     * Creates a custom aggregate operand.
     *
     * @param path    the property path.
     * @param aggFunc the aggregate function to apply.
     */
    public static AggregateOperand sum(final String path,
                                       final AggregateFunction aggFunc) {
        Objects.requireNonNull(aggFunc, "aggFunc must not be null");
        Objects.requireNonNull(path, "path must not be null");
        return new AggregateOperand(aggFunc, pathOperand(path));
    }
}

