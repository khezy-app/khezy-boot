package io.github.khezyapp.grammar.ast;

/**
 * Visitor interface for traversing and processing the Specification Abstract Syntax Tree (AST).
 *
 * @param <R> the return type of the visitor operations
 */
public interface SpecificationVisitor<R> {

    /**
     * Processes a query specification.
     * @param querySpec the query specification to visit
     * @return the result of the visit
     */
    R visitQuerySpec(QuerySpec querySpec);

    /**
     * Processes a logical OR specification.
     * @param orSpec the OR specification to visit
     * @return the result of the visit
     */
    R visitLogicalOrSpec(LogicalOrSpec orSpec);

    /**
     * Processes a logical AND specification.
     * @param andSpec the AND specification to visit
     * @return the result of the visit
     */
    R visitLogicalAndSpec(LogicalAndSpec andSpec);

    /**
     * Processes a binary comparison specification.
     * @param binaryComparisonSpec the binary comparison to visit
     * @return the result of the visit
     */
    R visitBinaryComparisonSpec(BinaryComparisonSpec binaryComparisonSpec);

    /**
     * Processes an IN comparison specification.
     * @param inComparisonSpec the IN comparison to visit
     * @return the result of the visit
     */
    R visitInComparisonSpec(InComparisonSpec inComparisonSpec);

    /**
     * Processes a BETWEEN comparison specification.
     * @param betweenComparisonSpec the BETWEEN comparison to visit
     * @return the result of the visit
     */
    R visitBetweenComparisonSpec(BetweenComparisonSpec betweenComparisonSpec);

    /**
     * Processes a unary comparison specification.
     * @param unaryComparisonSpec the unary comparison to visit
     * @return the result of the visit
     */
    R visitUnaryComparisonSpec(UnaryComparisonSpec unaryComparisonSpec);

}
