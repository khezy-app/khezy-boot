package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

import java.util.List;

/**
 * Represents a comparison specification checking if an operand's value exists within a list of values.
 *
 * @param left the operand to evaluate
 * @param right the list of operands to check against
 * @param joinType the type of join to apply
 */
public record InComparisonSpec(
        Operand left,
        List<Operand> right,
        JoinType joinType
) implements ASTSpec {

    /**
     * Creates a builder initialized with the current values of this specification.
     * @return a new builder instance
     */
    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.right = right;
        builder.joinType = joinType;
        return builder;
    }

    /**
     * Dispatches the visitor to the specific visit method for this IN comparison node.
     *
     * @param visitor the specification visitor
     * @param <R> the return type
     * @return the visitor result
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitInComparisonSpec(this);
    }

    /**
     * Fluent builder for creating {@link InComparisonSpec} instances.
     */
    public static final class Builder {
        private Operand left;
        private List<Operand> right;
        private JoinType joinType;

        /**
         * Sets the target operand.
         * @param left the operand
         * @return the builder
         */
        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        /**
         * Sets the list of candidate operands.
         * @param right the list of operands
         * @return the builder
         */
        public Builder right(final List<Operand> right) {
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
         * Constructs the {@link InComparisonSpec}.
         * @return a new specification instance
         */
        public InComparisonSpec build() {
            return new InComparisonSpec(left, right, joinType);
        }
    }
}
