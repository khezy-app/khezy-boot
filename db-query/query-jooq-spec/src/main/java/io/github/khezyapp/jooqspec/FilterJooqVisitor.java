package io.github.khezyapp.jooqspec;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Visitor implementation that translates an {@link ASTSpec} tree into jOOQ {@link Condition} and query components.
 * <p>
 * This class maps the abstract specification nodes to jOOQ's Domain Specific Language (DSL),
 * facilitating the dynamic generation of SQL clauses for WHERE, HAVING, and GROUP BY.
 * </p>
 */
public class FilterJooqVisitor implements SpecificationVisitor<JooqCondition> {

    /**
     * Visits the root query specification to build a {@code JooqSpecification} containing conditions and
     * grouping fields.
     *
     * @param querySpec the query specification to visit
     * @return a {@link JooqCondition} wrapping the primary WHERE logic
     */
    @Override
    public JooqCondition visitQuerySpec(final QuerySpec querySpec) {
        final var where = querySpec.whereSpec().accept(this);
        final var having = Objects.isNull(querySpec.havingSpec()) ? null : querySpec.havingSpec().accept(this);
        final var groupBy = resolveGroupBy(querySpec.groupBy());
        return new JooqSpecification.Builder()
                .where(where.condition())
                .groupBy(groupBy)
                .having(Objects.isNull(having) ? DSL.noCondition() : having.condition())
                .build();
    }

    /**
     * Translates a logical OR node into a combined jOOQ OR condition.
     *
     * @param orSpec the OR specification
     * @return a jOOQ condition representing the disjunction of child nodes
     */
    @Override
    public JooqCondition visitLogicalOrSpec(final LogicalOrSpec orSpec) {
        if (Objects.isNull(orSpec) ||
                orSpec.children().isEmpty()) {
            return new JooqConditionImpl(DSL.noCondition());

        }

        final var children = orSpec.children()
                .stream()
                .map(a -> a.accept(this))
                .toList();
        if (children.size() == 1) {
            return children.get(0);
        }

        final var reduceCondition = children.stream()
                .reduce(
                        DSL.noCondition(),
                        (old, curr) -> old.or(curr.condition()),
                        Condition::or
                );
        return new JooqConditionImpl(reduceCondition);
    }

    /**
     * Translates a logical AND node into a combined jOOQ AND condition.
     *
     * @param andSpec the AND specification
     * @return a jOOQ condition representing the conjunction of child nodes
     */
    @Override
    public JooqCondition visitLogicalAndSpec(final LogicalAndSpec andSpec) {
        if (Objects.isNull(andSpec) ||
                andSpec.children().isEmpty()) {
            return new JooqConditionImpl(DSL.noCondition());

        }

        final var children = andSpec.children()
                .stream()
                .map(a -> a.accept(this))
                .toList();
        if (children.size() == 1) {
            return children.get(0);
        }

        final var reduceCondition = children.stream()
                .reduce(
                        DSL.noCondition(),
                        (old, curr) -> old.and(curr.condition()),
                        Condition::and
                );
        return new JooqConditionImpl(reduceCondition);
    }

    /**
     * Translates a binary comparison node into a jOOQ comparison condition.
     *
     * @param binaryComparisonSpec the binary comparison details
     * @return the resulting jOOQ condition
     */
    @Override
    public JooqCondition visitBinaryComparisonSpec(final BinaryComparisonSpec binaryComparisonSpec) {
        final Field<?> leftOperand = resolveOperand(binaryComparisonSpec.left());
        final Field<?> rightOperand = resolveValue(binaryComparisonSpec.right());
        return newCondition(compare(leftOperand, binaryComparisonSpec.operator(), rightOperand));
    }

    /**
     * Maps comparison operators to jOOQ field comparison methods.
     *
     * @param left the left-hand field
     * @param op the comparison operator
     * @param right the right-hand field
     * @return the jOOQ condition
     * @throws IllegalArgumentException if the operator is unknown
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Condition compare(final Field<?> left,
                              final ComparisonOperator op,
                              final Field<?> right) {
        return switch (op) {
            case EQ -> left.eq((Field) right);
            case NE -> left.ne((Field) right);
            case LT -> left.lessThan((Field) right);
            case LTE -> left.lessOrEqual((Field) right);
            case GT -> left.greaterThan((Field) right);
            case GTE -> left.greaterOrEqual((Field) right);
            default -> throw new IllegalArgumentException("Unknown binary comparison operator: " + op);
        };
    }

    /**
     * Translates an IN comparison node into a jOOQ IN condition.
     *
     * @param inComparisonSpec the IN comparison details
     * @return the resulting jOOQ condition
     */
    @Override
    public JooqCondition visitInComparisonSpec(final InComparisonSpec inComparisonSpec) {
        final var values = inComparisonSpec.right()
                .stream()
                .map(this::resolveValue)
                .toList();
        final var leftOperand = resolveOperand(inComparisonSpec.left());
        return newCondition(
                leftOperand.in(values)
        );
    }

    /**
     * Translates a BETWEEN comparison node into a jOOQ BETWEEN condition.
     *
     * @param betweenComparisonSpec the BETWEEN comparison details
     * @return the resulting jOOQ condition
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public JooqCondition visitBetweenComparisonSpec(final BetweenComparisonSpec betweenComparisonSpec) {
        final var leftOperand = resolveOperand(betweenComparisonSpec.left());
        final var from = resolveValue(betweenComparisonSpec.from());
        final var to = resolveValue(betweenComparisonSpec.to());
        return newCondition(leftOperand.between((Field) from, (Field) to));
    }

    /**
     * Translates a unary comparison node (NULL checks) into a jOOQ condition.
     *
     * @param unaryComparisonSpec the unary comparison details
     * @return the resulting jOOQ condition
     */
    @Override
    public JooqCondition visitUnaryComparisonSpec(final UnaryComparisonSpec unaryComparisonSpec) {
        final var leftOperand = resolveOperand(unaryComparisonSpec.left());
        return newCondition(
                unaryComparison(leftOperand, unaryComparisonSpec.operator())
        );
    }

    /**
     * Maps unary operators to jOOQ null check methods.
     *
     * @param left the field to check
     * @param op the unary operator
     * @return the jOOQ condition
     */
    private Condition unaryComparison(final Field<?> left,
                                      final ComparisonOperator op) {
        return switch (op) {
            case IS_NULL -> left.isNull();
            case IS_NOT_NULL -> left.isNotNull();
            default -> throw new IllegalArgumentException("Unknown unary comparison operator: " + op);
        };
    }

    /**
     * Resolves the GROUP BY node into a list of jOOQ fields.
     *
     * @param groupBy the group by specification
     * @return a list of jOOQ fields for grouping
     */
    private List<Field<?>> resolveGroupBy(final GroupBy groupBy) {
        if (Objects.isNull(groupBy)) {
            return Collections.emptyList();
        }
        final var groupItems = new ArrayList<Field<?>>();
        for (final var item : Objects.requireNonNullElseGet(groupBy.items(), Collections::<Operand>emptyList)) {
            groupItems.add(resolveOperand(item));
        }
        return groupItems;
    }

    /**
     * Resolves an {@link Operand} into a jOOQ {@link Field}.
     *
     * @param operand the operand to resolve
     * @return the jOOQ field representation
     */
    private Field<?> resolveOperand(final Operand operand) {
        if (operand instanceof PathOperand pathOperand) {
            return resolvePathOperand(pathOperand);
        } else {
            return resolveAggregateOperand(((AggregateOperand) operand));
        }
    }

    /**
     * Resolves an {@link Operand} into a value-based jOOQ field (handles literals).
     *
     * @param operand the operand representing a value
     * @return a jOOQ field representing a value or a column reference
     */
    private Field<?> resolveValue(final Operand operand) {
        if (operand instanceof LiteralOperand literalOperand) {
            // Wrap the raw value (String, Long, etc.) into a jOOQ Field
            return DSL.val(literalOperand.value());
        }
        // If it's not a literal, it's a Path or Aggregate, so resolve it as a Field
        return resolveOperand(operand);
    }

    /**
     * Resolves a {@link PathOperand} into a qualified jOOQ field name.
     *
     * @param pathOperand the path operand
     * @return the jOOQ field
     */
    private Field<?> resolvePathOperand(final PathOperand pathOperand) {
        return DSL.field(DSL.name(pathOperand.identifiers()));
    }

    /**
     * Resolves an {@link AggregateOperand} into a jOOQ aggregate function expression.
     *
     * @param aggregateOperand the aggregate details
     * @return the jOOQ aggregate expression
     */
    private Field<?> resolveAggregateOperand(final AggregateOperand aggregateOperand) {
        return switch (aggregateOperand.function()) {
            case COUNT -> DSL.count(resolveOperand(aggregateOperand.path()));
            case SUM -> DSL.sum(resolveOperand(aggregateOperand.path()).cast(SQLDataType.NUMERIC));
            case AVG -> DSL.avg(resolveOperand(aggregateOperand.path()).cast(SQLDataType.NUMERIC));
            case MIN -> DSL.min(resolveOperand(aggregateOperand.path()));
            case MAX -> DSL.max(resolveOperand(aggregateOperand.path()));
        };
    }

    /**
     * Wraps a raw jOOQ {@link Condition} into a {@link JooqCondition} implementation.
     *
     * @param condition the jOOQ condition
     * @return the wrapped condition
     */
    private JooqCondition newCondition(final Condition condition) {
        return new JooqConditionImpl(condition);
    }
}
