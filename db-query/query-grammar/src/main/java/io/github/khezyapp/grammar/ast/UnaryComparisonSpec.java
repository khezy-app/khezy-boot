package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

/**
 * Represents a comparison specification involving a single operand and a unary operator.
 *
 * @param left the operand to be evaluated
 * @param operator the unary comparison operator to apply
 * @param joinType the type of join to apply
 */
public record UnaryComparisonSpec(
        Operand left,
        ComparisonOperator operator,
        JoinType joinType
) implements ASTSpec {

    /**
     * Returns a builder initialized with the current state of this specification.
     * @return a new builder instance
     */
    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.operator = operator;
        builder.joinType = joinType;
        return builder;
    }

    /**
     * Accepts a visitor to process this unary comparison.
     *
     * @param visitor the specification visitor
     * @param <R> the return type
     * @return the visitor result
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitUnaryComparisonSpec(this);
    }

    /**
     * Builder for creating instances of {@link UnaryComparisonSpec}.
     */
    public static final class Builder {
        private Operand left;
        private ComparisonOperator operator;
        private JoinType joinType;

        /**
         * Sets the operand.
         *
         * @param left the operand
         * @return the builder
         */
        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        /**
         * Sets the unary operator.
         *
         * @param operator the operator
         * @return the builder
         */
        public Builder operator(final ComparisonOperator operator) {
            this.operator = operator;
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
         * Builds a new {@link UnaryComparisonSpec}.
         * @return the specification instance
         */
        public UnaryComparisonSpec build() {
            return new UnaryComparisonSpec(left, operator, joinType);
        }
    }
}
