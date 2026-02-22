package io.github.khezyapp.grammar.ast.operand;

import java.util.List;

/**
 * Represents a reference to a specific property or field path within a data model.
 *
 * @param identifiers the individual segments of the path (e.g., ["user", "name"])
 * @param path the full dot-notation string representation (e.g., "user.name")
 */
public record PathOperand(List<String> identifiers, String path) implements Operand {
}
