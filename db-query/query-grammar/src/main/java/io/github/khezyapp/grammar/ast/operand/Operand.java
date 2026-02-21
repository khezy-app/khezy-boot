package io.github.khezyapp.grammar.ast.operand;

public sealed interface Operand permits PathOperand, LiteralOperand, AggregateOperand {
}
