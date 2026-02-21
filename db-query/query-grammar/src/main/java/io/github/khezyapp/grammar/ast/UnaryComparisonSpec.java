package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

public record UnaryComparisonSpec(
        Operand left,
        ComparisonOperator operator,
        JoinType joinType
) implements ASTSpec {

    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.operator = operator;
        builder.joinType = joinType;
        return builder;
    }

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitUnaryComparisonSpec(this);
    }

    public static final class Builder {
        private Operand left;
        private ComparisonOperator operator;
        private JoinType joinType;

        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        public Builder operator(final ComparisonOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder joinType(final JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        public UnaryComparisonSpec build() {
            return new UnaryComparisonSpec(left, operator, joinType);
        }
    }
}
