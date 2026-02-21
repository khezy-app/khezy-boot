package io.github.khezyapp.jpaspec;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.JoinType;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import jakarta.persistence.criteria.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class JpaSpecificationVisitor<T> implements SpecificationVisitor<Predicate> {
    private final Root<T> root;
    private final CriteriaBuilder cb;
    private final CriteriaQuery<?> query;

    public JpaSpecificationVisitor(final Root<T> root,
                                   final CriteriaQuery<?> query,
                                   final CriteriaBuilder cb) {
        this.root = root;
        this.cb = cb;
        this.query = query;
    }

    @Override
    public Predicate visitQuerySpec(final QuerySpec querySpec) {
        // 1. Handle WHERE
        final var where = querySpec.whereSpec().accept(this);

        // 2. Handle HAVING
        final var having = Objects.isNull(querySpec.havingSpec()) ?
                null : querySpec.havingSpec().accept(this);
        if (Objects.nonNull(having)) {
            query.having(having);
        }

        // 3. Handle GROUP BY
        if (Objects.nonNull(querySpec.groupBy())) {
            final var groupByItems = Objects.requireNonNullElse(querySpec.groupBy().items(),
                    Collections.<Operand>emptyList());
            if (!groupByItems.isEmpty()) {
                final List<Expression<?>> groups = groupByItems.stream()
                        .map(item -> getOperand(item, JoinType.LEFT))
                        .collect(Collectors.toList());
                query.groupBy(groups);
            }
        }

        return where;
    }

    @Override
    public Predicate visitLogicalOrSpec(final LogicalOrSpec orSpec) {
        if (Objects.isNull(orSpec) ||
                orSpec.children().isEmpty()) {
            return cb.conjunction();
        }
        if (orSpec.children().size() == 1) {
            return orSpec.children().get(0).accept(this);
        }
        final var predicates = orSpec.children()
                .stream()
                .map(child -> child.accept(this))
                .toList();
        return cb.or(predicates);
    }

    @Override
    public Predicate visitLogicalAndSpec(final LogicalAndSpec andSpec) {
        if (Objects.isNull(andSpec) ||
                andSpec.children().isEmpty()) {
            return cb.conjunction();
        }
        if (andSpec.children().size() == 1) {
            return andSpec.children().get(0).accept(this);
        }
        final var predicates = andSpec.children()
                .stream()
                .map(child -> child.accept(this))
                .toList();
        return cb.and(predicates);
    }

    @Override
    public Predicate visitBinaryComparisonSpec(final BinaryComparisonSpec binaryComparisonSpec) {
        final var expression = getOperand(binaryComparisonSpec.left(), binaryComparisonSpec.joinType());
        final var operator = binaryComparisonSpec.operator();
        final var value = getValue(binaryComparisonSpec.right(), binaryComparisonSpec.joinType());
        return switch (operator) {
            case EQ -> cb.equal(expression, value);
            case NE -> cb.notEqual(expression, value);
            case LT -> cb.lessThan(expression.as(Comparable.class), (Comparable<Object>) value);
            case LTE -> cb.lessThanOrEqualTo(expression.as(Comparable.class), (Comparable<Object>) value);
            case GT -> cb.greaterThan(expression.as(Comparable.class), (Comparable<Object>) value);
            case GTE -> cb.greaterThanOrEqualTo(expression.as(Comparable.class), (Comparable<Object>) value);
            default -> throw new UnsupportedOperationException("Operator '%s' not supported in binary comparison"
                    .formatted(operator));
        };
    }

    @Override
    public Predicate visitInComparisonSpec(final InComparisonSpec inComparisonSpec) {
        final var expression = getOperand(inComparisonSpec.left(), inComparisonSpec.joinType());
        final var values = inComparisonSpec.right()
                .stream()
                .map(op -> getValue(op, inComparisonSpec.joinType()))
                .toList();
        return expression.in(values);
    }

    @Override
    public Predicate visitBetweenComparisonSpec(final BetweenComparisonSpec betweenComparisonSpec) {
        final var joinType = betweenComparisonSpec.joinType();
        return cb.between(getOperand(betweenComparisonSpec.left(), joinType).as(Comparable.class),
                (Comparable<Object>) getValue(betweenComparisonSpec.from(), joinType),
                (Comparable<Object>) getValue(betweenComparisonSpec.to(), joinType));
    }

    @Override
    public Predicate visitUnaryComparisonSpec(final UnaryComparisonSpec unaryComparisonSpec) {
        final var path = getOperand(unaryComparisonSpec.left(), unaryComparisonSpec.joinType());
        return switch (unaryComparisonSpec.operator()) {
            case IS_NULL -> cb.isNull(path);
            case IS_NOT_NULL -> cb.isNotNull(path);
            default -> throw new UnsupportedOperationException("Operator '%s' not supported unary"
                    .formatted(unaryComparisonSpec.operator()));
        };
    }

    private Expression<?> getOperand(final Operand operand,
                                     final JoinType joinType) {
        if (operand instanceof PathOperand pathOperand) {
            return getPath(pathOperand, joinType);
        } else {
            return getAggregate((AggregateOperand) operand, joinType);
        }
    }

    // Helper to handle nested paths like "user.address.city"
    private Path<?> getPath(final PathOperand path,
                            final JoinType joinType) {
        final var parts = path.identifiers();
        From<?, ?> currentFrom = root; // 'root' is the starting point

        for (var i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            // Reuse existing join if already present for this segment
            final var elseFrom = currentFrom;
            currentFrom = currentFrom.getJoins()
                    .stream()
                    .filter(j -> j.getAttribute().getName().equals(part))
                    .findFirst()
                    .map(j -> (From<?, ?>) j)
                    .orElseGet(() -> elseFrom.join(part, jpaJoin(joinType)));
        }

        // The final segment is the attribute (column) name
        return currentFrom.get(parts.get(parts.size() - 1));
    }

    private Expression<?> getAggregate(final AggregateOperand agg,
                                      final JoinType joinType) {
        return switch (agg.function()) {
            case COUNT -> cb.count(getPath(agg.path(), joinType));
            case SUM -> cb.sum((Expression<? extends Number>) getPath(agg.path(), joinType));
            case AVG -> cb.avg((Expression<? extends Number>) getPath(agg.path(), joinType));
            case MIN -> cb.min((Expression<? extends Number>) getPath(agg.path(), joinType));
            case MAX -> cb.max((Expression<? extends Number>) getPath(agg.path(), joinType));
        };
    }

    private Object getValue(final Operand operand,
                            final JoinType joinType) {
        if (operand instanceof LiteralOperand literalOperand) {
            return literalOperand.value();
        } else {
            return getOperand(operand, joinType);
        }
    }

    private jakarta.persistence.criteria.JoinType jpaJoin(final JoinType joinType) {
        return switch (joinType) {
            case LEFT -> jakarta.persistence.criteria.JoinType.LEFT;
            case RIGHT -> jakarta.persistence.criteria.JoinType.RIGHT;
            default -> jakarta.persistence.criteria.JoinType.INNER;
        };
    }
}
