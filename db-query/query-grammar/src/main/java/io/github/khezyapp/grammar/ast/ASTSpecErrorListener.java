package io.github.khezyapp.grammar.ast;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class ASTSpecErrorListener extends BaseErrorListener {
    public static final ASTSpecErrorListener INSTANCE = new ASTSpecErrorListener();

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer,
                            final Object offendingSymbol,
                            final int line,
                            final int charPositionInLine,
                            final String msg,
                            final RecognitionException e) {
        throw new IllegalArgumentException(
                String.format("Invalid query at line %d:%d - %s", line, charPositionInLine, msg)
        );
    }
}
