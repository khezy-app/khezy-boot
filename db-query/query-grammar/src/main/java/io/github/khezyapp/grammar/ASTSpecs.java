package io.github.khezyapp.grammar;

import io.github.khezyapp.grammar.ast.QuerySpec;
import io.github.khezyapp.query.FilterSpecLexer;
import io.github.khezyapp.query.FilterSpecParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public final class ASTSpecs {

    private ASTSpecs() {
    }

    public static QuerySpec fromQuery(final String filterQuery) {
        final var lexer = new FilterSpecLexer(CharStreams.fromString(filterQuery));
        final var token = new CommonTokenStream(lexer);
        final var parser = new FilterSpecParser(token);
        final var queryRoot = parser.filterSpec();
        final var queryVisitor = new ASTSpecVisitor();
        return (QuerySpec) queryVisitor.visit(queryRoot);
    }

}
