package io.github.khezyapp.api.security;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe utility for evaluating SpEL expressions with an internal
 * cache. Used to evaluate row-level security (RLS) expressions defined
 * in {@code @PreFilter} / {@code @PostFilter} annotations.
 */
public final class Expressions {
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> PARSER_CACHE = new ConcurrentHashMap<>();

    private Expressions() {
    }

    /**
     * Evaluates a SpEL expression against the given evaluation context.
     *
     * @param expression the SpEL expression string
     * @param context    the evaluation context (e.g. method arguments, root object)
     * @return the result of the expression evaluation
     * @throws SecurityException if evaluation fails
     */
    public static Object evaluate(final String expression,
                                  final EvaluationContext context) {
        try {
            final var expressionObj = getExpression(expression);
            return expressionObj.getValue(context);
        } catch (final Exception e) {
            throw new SecurityException("Failed to evaluate RLS expression: " + expression, e);
        }
    }

    /**
     * Evaluates a SpEL expression and returns the result cast to the specified
     * type.
     *
     * @param expression the SpEL expression string
     * @param context    the evaluation context
     * @param result     the expected result type
     * @return the typed result of the expression evaluation
     * @throws SecurityException if evaluation fails
     */
    public static <T> T evaluate(final String expression,
                                 final EvaluationContext context,
                                 final Class<T> result) {
        try {
            final var expressionObj = getExpression(expression);
            return expressionObj.getValue(context, result);
        } catch (final Exception e) {
            throw new SecurityException("Failed to evaluate RLS expression: " + expression, e);
        }
    }

    private static Expression getExpression(final String expression) {
        return PARSER_CACHE.computeIfAbsent(expression, key -> PARSER.parseExpression(expression));
    }

}
