package io.github.khezyapp.grammar.ast;

public record QuerySpec(
        ASTSpec whereSpec,
        ASTSpec havingSpec,
        GroupBy groupBy
) implements ASTSpec {

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitQuerySpec(this);
    }
}
