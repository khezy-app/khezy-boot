package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

/**
 * Represents a comparison specification between two operands using a binary operator.
 *
 * @param left the left-hand operand
 * @param operator the comparison operator to apply
 * @param right the right-hand operand
 * @param joinType the type of join to apply
 */
public record BinaryComparisonSpec(
        Operand left,
        ComparisonOperator operator,
        Operand right,
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
        builder.right = right;
        builder.joinType = joinType;
        return builder;
    }

    /**
     * Accepts a visitor to process this binary comparison.
     * @param visitor the specification visitor
     * @param <R> the return type
     * @return the visitor result
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitBinaryComparisonSpec(this);
    }

    /**
     * Builder for creating instances of {@link BinaryComparisonSpec}.
     */
    public static final class Builder {
        private Operand left;
        private ComparisonOperator operator;
        private Operand right;
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
         * Sets the comparison operator.
         * @param operator the operator
         * @return the builder
         */
        public Builder operator(final ComparisonOperator operator) {
            this.operator = operator;
            return this;
        }

        /**
         * Sets the right-hand operand.
         * @param right the operand
         * @return the builder
         */
        public Builder right(final Operand right) {
            this.right = right;
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
         * Builds a new {@link BinaryComparisonSpec}.
         * @return the specification instance
         */
        public BinaryComparisonSpec build() {
            return new BinaryComparisonSpec(left, operator, right, joinType);
        }
    }
}
