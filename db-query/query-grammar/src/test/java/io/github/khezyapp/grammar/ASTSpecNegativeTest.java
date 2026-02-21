package io.github.khezyapp.grammar;

import io.github.khezyapp.query.FilterSpecLexer;
import io.github.khezyapp.query.FilterSpecParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASTSpecNegativeTest {

    private FilterSpecParser getParser(final String query) {
        final var lexer = new FilterSpecLexer(CharStreams.fromString(query));
        final var tokens = new CommonTokenStream(lexer);
        return new FilterSpecParser(tokens);
    }

    @Test
    void testInvalidComparisonMissingOperator() {
        final var query = "user.age 18";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testInvalidIdentifierStartingWithDigit() {
        final var query = "1user.name = 'John'";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testUnclosedParenthesis() {
        final var query = "(age > 18 AND status = 'ACTIVE'";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testInvalidInClauseMissingClosingParen() {
        final var query = "id IN (1, 2, 3";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testBetweenClauseMissingAnd() {
        final var query = "age BETWEEN 10 20";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testEmptyInClause() {
        final var query = "id IN ()";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testInvalidAggregateNesting() {
        final var query = "SUM(COUNT(id)) > 0";
        final var parser = getParser(query);
        parser.filterSpec();

        // Grammar 'path' rule doesn't allow aggregateFunction inside COUNT/SUM
        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testDanglingLogicalOperator() {
        final var query = "age > 18 AND";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }

    @Test
    void testMissingGroupByItems() {
        final var query = "id = 1 GROUP BY";
        final var parser = getParser(query);
        parser.filterSpec();

        assertTrue(parser.getNumberOfSyntaxErrors() > 0);
    }
}
