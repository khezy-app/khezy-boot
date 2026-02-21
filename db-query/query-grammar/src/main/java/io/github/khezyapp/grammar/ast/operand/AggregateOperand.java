package io.github.khezyapp.grammar.ast.operand;

import io.github.khezyapp.grammar.ast.AggregateFunction;

public record AggregateOperand(AggregateFunction function, PathOperand path) implements Operand {
}
