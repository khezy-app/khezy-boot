package io.github.khezyapp.api.audit;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Evaluates SpEL (Spring Expression Language) expressions within the context of a
 * method invocation to dynamically extract identifiers or metadata.
 * <p>
 * This evaluator is primarily used to resolve the {@code entityId} defined in the
 * {@link io.github.khezyapp.api.audit.annotation.AuditLog} annotation. It supports accessing method arguments by name,
 * positional indices, and the special {@code #result} variable representing the
 * method's return value.
 * </p>
 * <p>
 * To optimize performance, parsed expressions are maintained in a
 * {@link ConcurrentLruCache}.
 * </p>
 */
@Slf4j
public class AuditExpressionEvaluator {
    /**
     * The standard Spring SpEL expression parser.
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * An LRU cache to store parsed expressions, reducing the overhead of
     * repeated parsing for the same expression strings.
     */
    private final ConcurrentLruCache<String, Expression> expressionCache =
            new ConcurrentLruCache<>(512, parser::parseExpression);

    /**
     * Discoverer used to resolve method parameter names (e.g., via debug symbols
     * or standard reflection).
     */
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * Executes the provided SpEL expression against the method invocation context.
     * <p>
     * The evaluation context provides:
     * <ul>
     * <li><b>Method Arguments:</b> Accessible by their parameter names (e.g., {@code #id}).</li>
     * <li><b>Method Result:</b> Accessible via the {@code #result} variable (available only
     * after method execution).</li>
     * <li><b>Target Object:</b> The instance on which the method was invoked.</li>
     * </ul>
     * </p>
     *
     * @param expressionString the raw SpEL expression to evaluate
     * @param invocation the current AOP method invocation
     * @param result the object returned by the intercepted method (may be {@code null})
     * @return the evaluated string result, {@code null} if the expression is empty,
     * or {@code "EXPRESSION_ERROR"} if evaluation fails
     */
    public String execute(final String expressionString,
                          final MethodInvocation invocation,
                          final Object result) {
        if (!StringUtils.hasText(expressionString)) {
            return null;
        }

        final var method = invocation.getMethod();
        final var args = invocation.getArguments();

        // 1. Get or Parse Expression
        final var expression = expressionCache.get(expressionString);

        // 2. Create Context (Supports #body, #id, etc. by parameter name)
        final var context = new MethodBasedEvaluationContext(invocation.getThis(), method, args, nameDiscoverer);

        // 3. Add #result variable so we can audit generated IDs
        if (Objects.nonNull(result)) {
            context.setVariable("result", result);
        }

        try {
            final var value = expression.getValue(context);
            return Objects.nonNull(value) ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Error during extract resource id from expression string {}", expressionString, e);
            return "EXPRESSION_ERROR";
        }
    }
}
