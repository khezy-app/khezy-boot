package io.github.khezyapp.grammar.ast.operand;

/**
 * Represents a constant or literal value used as an operand in a specification.
 *
 * @param value the actual value, such as a String, Number, or Boolean
 */
public record LiteralOperand(Object value) implements Operand {
}
