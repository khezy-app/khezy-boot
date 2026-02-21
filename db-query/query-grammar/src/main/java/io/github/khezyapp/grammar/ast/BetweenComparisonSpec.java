package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

public record BetweenComparisonSpec(
        Operand left,
        Operand from,
        Operand to,
        JoinType joinType
) implements ASTSpec {

    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.from = from;
        builder.to = to;
        builder.joinType = joinType;
        return builder;
    }

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitBetweenComparisonSpec(this);
    }

    public static final class Builder {
        private Operand left;
        private Operand from;
        private Operand to;
        private JoinType joinType;

        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }

        public Builder from(final Operand from) {
            this.from = from;
            return this;
        }

        public Builder to(final Operand to) {
            this.to = to;
            return this;
        }

        public Builder joinType(final JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        public BetweenComparisonSpec build() {
            return new BetweenComparisonSpec(left, from, to, joinType);
        }
    }
}
