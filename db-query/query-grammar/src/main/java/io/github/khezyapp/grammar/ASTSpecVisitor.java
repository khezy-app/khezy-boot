package io.github.khezyapp.grammar;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.AggregateOperand;
import io.github.khezyapp.grammar.ast.operand.LiteralOperand;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;
import io.github.khezyapp.query.FilterSpecBaseVisitor;
import io.github.khezyapp.query.FilterSpecParser;
import org.antlr.v4.runtime.RuleContext;

import java.util.List;
import java.util.Objects;

public class ASTSpecVisitor extends FilterSpecBaseVisitor<ASTSpec> {

    @Override
    public ASTSpec visitRootQuery(final FilterSpecParser.RootQueryContext ctx) {
        final var where = visit(ctx.where);
        final var having = Objects.isNull(ctx.havingClause()) ? null : visit(ctx.havingClause());
        final var groupBy = resolveGroupBy(ctx.groupByClause());
        return new QuerySpec(where, having, groupBy);
    }

    private GroupBy resolveGroupBy(final FilterSpecParser.GroupByClauseContext ctx) {
        if (Objects.isNull(ctx)) {
            return null;
        }
        final var values = ctx.groupItem()
                .stream()
                .map(this::resolveGroupItemContext)
                .toList();
        return new GroupBy(values);
    }

    @Override
    public ASTSpec visitHavingClause(final FilterSpecParser.HavingClauseContext ctx) {
        return visit(ctx.orExpr());
    }

    @Override
    public ASTSpec visitLogicalOrExpr(final FilterSpecParser.LogicalOrExprContext ctx) {
        final var orExpr = ctx.andExpr()
                .stream()
                .map(this::visit)
                .filter(Objects::nonNull)
                .toList();
        return new LogicalOrSpec(orExpr);
    }

    @Override
    public ASTSpec visitLogicalAndExpr(final FilterSpecParser.LogicalAndExprContext ctx) {
        final var andExpr = ctx.primaryExpr()
                .stream()
                .map(this::visit)
                .filter(Objects::nonNull)
                .toList();
        return new LogicalAndSpec(andExpr);
    }

    @Override
    public ASTSpec visitParenExpr(final FilterSpecParser.ParenExprContext ctx) {
        return visit(ctx.orExpr());
    }

    @Override
    public ASTSpec visitBaseComparison(final FilterSpecParser.BaseComparisonContext ctx) {
        final var left = resolveSelectableContext(ctx.left);
        final var joinType = resolveJoinType(ctx);
        final var rest = visit(ctx.comparisonRest());
        if (rest instanceof BinaryComparisonSpec cs) {
            return cs.mutate()
                    .left(left)
                    .joinType(joinType)
                    .build();
        } else if (rest instanceof InComparisonSpec is) {
            return is.mutate()
                    .left(left)
                    .joinType(joinType)
                    .build();
        } else if (rest instanceof BetweenComparisonSpec bs) {
            return bs.mutate()
                    .left(left)
                    .joinType(joinType)
                    .build();
        } else if (rest instanceof UnaryComparisonSpec us) {
            return us.mutate()
                    .left(left)
                    .joinType(joinType)
                    .build();
        }
        throw new IllegalArgumentException("Unknow comparison type class: " + rest.getClass().getSimpleName());
    }

    @Override
    public ASTSpec visitSimpleComparison(final FilterSpecParser.SimpleComparisonContext ctx) {
        final var operator = ComparisonOperator.of(ctx.operator().getText());
        final var value = resolveValueContext(ctx.value());
        return new BinaryComparisonSpec.Builder()
                .operator(operator)
                .right(value)
                .build();
    }

    @Override
    public ASTSpec visitInComparison(final FilterSpecParser.InComparisonContext ctx) {
        final var values = ctx.value().stream()
                .map(this::resolveValueContext)
                .toList();
        return new InComparisonSpec.Builder()
                .right(values)
                .build();
    }

    @Override
    public ASTSpec visitBetweenComparison(final FilterSpecParser.BetweenComparisonContext ctx) {
        final var from = resolveValueContext(ctx.start);
        final var to = resolveValueContext(ctx.end);
        return new BetweenComparisonSpec.Builder()
                .from(from)
                .to(to)
                .build();
    }

    @Override
    public ASTSpec visitNullComparison(final FilterSpecParser.NullComparisonContext ctx) {
        return new UnaryComparisonSpec.Builder()
                .operator(ComparisonOperator.IS_NULL)
                .build();
    }

    @Override
    public ASTSpec visitNotNullComparison(final FilterSpecParser.NotNullComparisonContext ctx) {
        return new UnaryComparisonSpec.Builder()
                .operator(ComparisonOperator.IS_NOT_NULL)
                .build();
    }

    private JoinType resolveJoinType(final FilterSpecParser.BaseComparisonContext ctx) {
        return Objects.isNull(ctx.joinType()) ?
                JoinType.INNER :
                JoinType.of(ctx.joinType().getText());
    }


    private Operand resolveSelectableContext(final FilterSpecParser.SelectableContext ctx) {
        if (ctx instanceof FilterSpecParser.PathSelectableContext pathCtx) {
           return resolvePathOperand(pathCtx.path());
        } else {
            final var aggCtx = (FilterSpecParser.AggregateSelectableContext) ctx;
            return resolveAggOperand(aggCtx.aggregateFunction());
        }
    }

    private Operand resolveGroupItemContext(final FilterSpecParser.GroupItemContext ctx) {
        if (ctx instanceof FilterSpecParser.PathGroupItemContext pathCtx) {
            return resolvePathOperand(pathCtx.path());
        } else {
            final var aggCtx = (FilterSpecParser.AggregateGroupItemContext) ctx;
            return resolveAggOperand(aggCtx.aggregateFunction());
        }
    }

    private PathOperand resolvePathOperand(final FilterSpecParser.PathContext ctx) {
        final var identifiers = ctx.anyIdentifier()
                .stream()
                .map(RuleContext::getText)
                .toList();
        return new PathOperand(identifiers, ctx.getText());
    }

    private AggregateOperand resolveAggOperand(final FilterSpecParser.AggregateFunctionContext ctx) {
        final var pathOperand = Objects.nonNull(ctx.path()) ?
                resolvePathOperand(ctx.path()) :
                new PathOperand(List.of("*"), "*");
        final AggregateFunction aggFunc;
        if (Objects.nonNull(ctx.COUNT())) {
            aggFunc = AggregateFunction.COUNT;
        } else if (Objects.nonNull(ctx.SUM())) {
            aggFunc = AggregateFunction.SUM;
        } else if (Objects.nonNull(ctx.AVG())) {
            aggFunc = AggregateFunction.AVG;
        } else if (Objects.nonNull(ctx.MIN())) {
            aggFunc = AggregateFunction.MIN;
        } else {
            aggFunc = AggregateFunction.MAX;
        }
        return new AggregateOperand(aggFunc, pathOperand);
    }

    private Operand resolveValueContext(final FilterSpecParser.ValueContext ctx) {
        if (Objects.nonNull(ctx.path())) {
            return resolvePathOperand(ctx.path());
        } else if (Objects.nonNull(ctx.aggregateFunction())) {
            return resolveAggOperand(ctx.aggregateFunction());
        } else {
            if (Objects.nonNull(ctx.STRING())) {
                var string = ctx.STRING().getText();
                if (string.startsWith("'")) {
                    string = string.substring(1);
                }
                if (string.endsWith("'")) {
                    string = string.substring(0, string.length() - 1);
                }
                return new LiteralOperand(string);
            }

            // Else it is number rule
            final var number = ctx.NUMBER().getText();
            final Object value;
            if (number.contains(".")) {
                value = Double.parseDouble(number);
            } else {
                value = Long.parseLong(number);
            }
            return new LiteralOperand(value);
        }
    }

}
