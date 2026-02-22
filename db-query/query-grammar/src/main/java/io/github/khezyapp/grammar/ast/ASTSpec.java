package io.github.khezyapp.grammar.ast;

/**
 * Root interface for the Specification Abstract Syntax Tree (AST) supporting visitor pattern traversal.
 */
public sealed interface ASTSpec permits BetweenComparisonSpec, BinaryComparisonSpec,
        InComparisonSpec, LogicalAndSpec, LogicalOrSpec, QuerySpec,
        UnaryComparisonSpec {

    /**
     * Accepts a visitor to process the AST node.
     *
     * @param visitor the specification visitor
     * @param <R> the return type of the visitor operation
     * @return the result produced by the visitor
     */
    <R> R accept(SpecificationVisitor<R> visitor);
}
