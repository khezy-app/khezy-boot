package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

import java.util.List;

public record InComparisonSpec(
        Operand left,
        List<Operand> right,
        JoinType joinType
) implements ASTSpec {

    public Builder mutate() {
        final var builder = new Builder();
        builder.left = left;
        builder.right = right;
        builder.joinType = joinType;
        return builder;
    }

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitInComparisonSpec(this);
    }

    public static final class Builder {
        private Operand left;
        private List<Operand> right;
        private JoinType joinType;

        public Builder left(final Operand left) {
            this.left = left;
            return this;
        }


        public Builder right(final List<Operand> right) {
            this.right = right;
            return this;
        }

        public Builder joinType(final JoinType joinType) {
            this.joinType = joinType;
            return this;
        }

        public InComparisonSpec build() {
            return new InComparisonSpec(left, right, joinType);
        }
    }
}
