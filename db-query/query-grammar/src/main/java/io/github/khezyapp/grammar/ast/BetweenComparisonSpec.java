package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

/**
 * Represents a comparison specification for a value being between two bounds.
 *
 * @param left the operand to evaluate
 * @param from the lower bound operand
 * @param to the upper bound operand
 * @param joinType the type of join to apply
 */
public record BetweenComparisonSpec(
        Operand left,
        Operand from,
        Operand to,
        JoinType joinType
) implements ASTSpec {

    /**
     * Creates a builder initialized with the current values of this specification.
     * @return a new builder instance
     */
    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.from = from;
        builder.to = to;
        builder.joinType = joinType;
        return builder;
    }

    /**
     * Dispatches the visitor to the specific visit method for this node.
     *
     * @param visitor the specification visitor
     * @param <R> the return type
     * @return the visitor result
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitBetweenComparisonSpec(this);
    }

    /**
     * Fluent builder for creating {@link BetweenComparisonSpec} instances.
     */
    public static final class Builder {
        private Operand left;
        private Operand from;
        private Operand to;
        private JoinType joinType;

        /**
         * Sets the left-hand operand.
         * @param left the operand
         * @return the builder
         */
        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        /**
         * Sets the lower bound operand.
         * @param from the operand
         * @return the builder
         */
        public Builder from(final Operand from) {
            this.from = from;
            return this;
        }

        /**
         * Sets the upper bound operand.
         * @param to the operand
         * @return the builder
         */
        public Builder to(final Operand to) {
            this.to = to;
            return this;
        }

        /**
         * Sets the join type.
         * @param joinType the join type
         * @return the builder
         */
        public Builder joinType(final JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        /**
         * Constructs the {@link BetweenComparisonSpec}.
         * @return a new specification instance
         */
        public BetweenComparisonSpec build() {
            return new BetweenComparisonSpec(left, from, to, joinType);
        }
    }
}
