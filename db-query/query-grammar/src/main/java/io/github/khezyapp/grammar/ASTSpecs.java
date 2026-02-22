package io.github.khezyapp.grammar;

import io.github.khezyapp.grammar.ast.QuerySpec;
import io.github.khezyapp.query.FilterSpecLexer;
import io.github.khezyapp.query.FilterSpecParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Utility class for parsing and creating {@link QuerySpec} instances from query strings.
 */
public final class ASTSpecs {

    private ASTSpecs() {
    }

    /**
     * Parses a raw filter query string into a {@link QuerySpec} using ANTLR.
     *
     * @param filterQuery the query string to parse
     * @return the parsed query specification root
     */
    public static QuerySpec fromQuery(final String filterQuery) {
        final var lexer = new FilterSpecLexer(CharStreams.fromString(filterQuery));
        final var token = new CommonTokenStream(lexer);
        final var parser = new FilterSpecParser(token);
        final var queryRoot = parser.filterSpec();
        final var queryVisitor = new ASTSpecVisitor();
        return (QuerySpec) queryVisitor.visit(queryRoot);
    }

}
