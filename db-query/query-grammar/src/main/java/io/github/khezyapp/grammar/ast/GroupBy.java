package io.github.khezyapp.grammar.ast;

import io.github.khezyapp.grammar.ast.operand.Operand;

import java.util.List;

/**
 * Represents the GROUP BY clause containing a list of operands to group the results by.
 *
 * @param items the list of operands (typically path operands) used for grouping
 */
public record GroupBy(List<Operand> items)  {
}
