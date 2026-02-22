package io.github.khezyapp.grammar.ast;

import java.util.List;

/**
 * Represents a logical AND specification that evaluates multiple child specifications.
 *
 * @param children the list of child specifications to be combined with AND logic
 */
public record LogicalAndSpec(List<ASTSpec> children) implements ASTSpec {

    /**
     * Accepts a visitor to process the logical AND node and its children.
     *
     * @param visitor the specification visitor
     * @param <R> the return type of the visitor operation
     * @return the result produced by the visitor
     */
    @Override
    public <R> R accept(final SpecificationVisitor<R> visitor) {
        return visitor.visitLogicalAndSpec(this);
    }
}
