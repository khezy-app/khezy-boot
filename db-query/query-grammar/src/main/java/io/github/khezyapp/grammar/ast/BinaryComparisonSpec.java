package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

public record BinaryComparisonSpec(
        Operand left,
        ComparisonOperator operator,
        Operand right,
        JoinType joinType
) implements ASTSpec {

    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.operator = operator;
        builder.right = right;
        builder.joinType = joinType;
        return builder;
    }

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitBinaryComparisonSpec(this);
    }

    public static final class Builder {
        private Operand left;
        private ComparisonOperator operator;
        private Operand right;
        private JoinType joinType;

        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        public Builder operator(final ComparisonOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder right(final Operand right) {
            this.right = right;
            return this;
        }

        public Builder joinType(final JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        public BinaryComparisonSpec build() {
            return new BinaryComparisonSpec(left, operator, right, joinType);
        }
    }
}
