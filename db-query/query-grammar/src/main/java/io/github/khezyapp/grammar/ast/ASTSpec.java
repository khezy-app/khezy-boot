package io.github.khezyapp.grammar.ast;

public sealed interface ASTSpec permits BetweenComparisonSpec, BinaryComparisonSpec,
        InComparisonSpec, LogicalAndSpec, LogicalOrSpec, QuerySpec,
        UnaryComparisonSpec {

    <R> R accept(SpecificationVisitor<R> visitor);
}
