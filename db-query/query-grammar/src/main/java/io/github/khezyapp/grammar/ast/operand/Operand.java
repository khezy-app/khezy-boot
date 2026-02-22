package io.github.khezyapp.grammar.ast.operand;

/**
 * Root interface representing an operand within a specification expression.
 * <p>
 * Supported types include paths, literal values, and aggregate functions.
 * </p>
 */
public sealed interface Operand permits PathOperand, LiteralOperand, AggregateOperand {
}
