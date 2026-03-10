package io.github.khezyapp.grammar.ast.builder;

import io.github.khezyapp.grammar.ast.*;
import io.github.khezyapp.grammar.ast.operand.Operand;
import io.github.khezyapp.grammar.ast.operand.PathOperand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of the Abstract Syntax Tree (AST) query specification builder.
 * This class employs the Fluent Builder and Step Builder patterns to guide the construction
 * of a {@link QuerySpec} through defined logical steps: Where, GroupBy, and Having.
 */
public final class ASTQuerySpecBuilder implements
        QuerySpecBuilderStep.WhereStep,
        QuerySpecBuilderStep.HavingStep,
        QuerySpecBuilderStep.GroupByStep {

    private ASTSpec where;
    private ASTSpec having;
    private GroupBy groupBy;

    private ASTQuerySpecBuilder() {
    }

    /**
     * Initializes the builder process.
     *
     * @return the initial step (WhereStep) to begin building the query specification.
     */
    public static QuerySpecBuilderStep.WhereStep builder() {
        return new ASTQuerySpecBuilder();
    }

    /**
     * Configures the WHERE clause of the query.
     *
     * @param spec the AST specification representing the filter criteria.
     * @return the next step in the builder sequence (GroupByStep).
     */
    @Override
    public QuerySpecBuilderStep.GroupByStep where(final ASTSpec spec) {
        this.where = ensureVisitorStructure(spec);
        return this;
    }

    /**
     * Configures the GROUP BY clause using string-based property paths.
     *
     * @param paths varargs of dot-notated strings representing the paths to group by.
     * @return the next step in the builder sequence (GroupByStep).
     */
    @Override
    public QuerySpecBuilderStep.GroupByStep groupBy(final String... paths) {
        final List<Operand> operands = Arrays.stream(paths)
                .map(p -> new PathOperand(List.of(p.split("\\.")), p))
                .collect(Collectors.toList());
        this.groupBy = new GroupBy(operands);
        return this;
    }

    /**
     * Configures the GROUP BY clause using specific Operand instances.
     *
     * @param operands varargs of {@link Operand} to be used for grouping.
     * @return the next step in the builder sequence (GroupByStep).
     */
    @Override
    public QuerySpecBuilderStep.GroupByStep groupBy(final Operand... operands) {
        this.groupBy = new GroupBy(Arrays.asList(operands));
        return this;
    }

    /**
     * Configures the HAVING clause of the query.
     *
     * @param spec the AST specification representing the aggregate filter criteria.
     * @return the final build step (BuildStep).
     */
    @Override
    public QuerySpecBuilderStep.BuildStep having(final ASTSpec spec) {
        this.having = ensureVisitorStructure(spec);
        return this;
    }

    /**
     * Constructs the final {@link QuerySpec} instance based on the provided parameters.
     *
     * @return a fully constructed QuerySpec object.
     */
    @Override
    public QuerySpec build() {
        return new QuerySpec(where, having, groupBy);
    }

    /**
     * Normalizes the AST structure to ensure compatibility with the Visitor pattern.
     * Wraps single specifications into a consistent hierarchy: Root -> LogicalOr -> LogicalAnd -> Spec.
     *
     * @param spec the raw AST specification.
     * @return a structured ASTSpec ready for visitor traversal.
     */
    private ASTSpec ensureVisitorStructure(final ASTSpec spec) {
        if (Objects.isNull(spec)) {
            return new LogicalOrSpec(Collections.emptyList());
        }

        if (spec instanceof LogicalOrSpec) {
            return spec;
        }

        // Visitor: root -> LogicalOr -> LogicalAnd -> Actual Spec
        final var andWrapped = (spec instanceof LogicalAndSpec) ? spec : new LogicalAndSpec(List.of(spec));
        return new LogicalOrSpec(List.of(andWrapped));
    }
}
