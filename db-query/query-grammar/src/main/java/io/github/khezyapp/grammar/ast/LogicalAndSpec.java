package io.github.khezyapp.grammar.ast;

import java.util.List;

public record LogicalAndSpec(List<ASTSpec> children) implements ASTSpec {

    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitLogicalAndSpec(this);
    }
}
