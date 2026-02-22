package io.github.khezyapp.grammar.ast.operand;

import io.github.khezyapp.grammar.ast.AggregateFunction;

/**
 * Represents an operand involving an aggregate function applied to a specific path.
 *
 * @param function the aggregate function to apply (e.g., SUM, COUNT, AVG)
 * @param path the path operand the function operates on
 */
public record AggregateOperand(AggregateFunction function, PathOperand path) implements Operand {
}
