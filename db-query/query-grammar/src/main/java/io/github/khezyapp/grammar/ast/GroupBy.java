package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

import java.util.List;

public record GroupBy(List<Operand> items)  {
}
