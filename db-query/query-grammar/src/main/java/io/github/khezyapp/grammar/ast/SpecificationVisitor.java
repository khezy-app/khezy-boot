package io.github.khezyapp.grammar.ast;

public interface SpecificationVisitor<R> {

    R visitQuerySpec(QuerySpec querySpec);

    R visitLogicalOrSpec(LogicalOrSpec orSpec);

    R visitLogicalAndSpec(LogicalAndSpec andSpec);

    R visitBinaryComparisonSpec(BinaryComparisonSpec binaryComparisonSpec);

    R visitInComparisonSpec(InComparisonSpec inComparisonSpec);

    R visitBetweenComparisonSpec(BetweenComparisonSpec betweenComparisonSpec);

    R visitUnaryComparisonSpec(UnaryComparisonSpec unaryComparisonSpec);

}
