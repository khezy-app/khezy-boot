package io.github.khezyapp.grammar.ast;

/**
 * Represents the top-level query specification containing filtering and grouping logic.
 *
 * @param whereSpec the specification for the WHERE clause filtering
 * @param havingSpec the specification for the HAVING clause filtering
 * @param groupBy the grouping definitions for the query
 */
public record QuerySpec(
        ASTSpec whereSpec,
        ASTSpec havingSpec,
        GroupBy groupBy
) implements ASTSpec {

    /**
     * Accepts a visitor to process the query specification.
     *
     * @param visitor the specification visitor
     * @param <R> the return type of the visitor operation
     * @return the result produced by the visitor
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitQuerySpec(this);
    }
}
