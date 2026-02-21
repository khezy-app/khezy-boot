package io.github.khezyapp.grammar.ast.operand;

import java.util.List;

public record PathOperand(List<String> identifiers, String path) implements Operand {
}
