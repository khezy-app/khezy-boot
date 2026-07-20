package io.github.khezyapp.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionsTest {

    @Test
    void shouldEvaluateSimpleExpression() {
        final var ctx = new StandardEvaluationContext();
        ctx.setVariable("value", 42);

        final var result = Expressions.evaluate("#value * 2", ctx);

        assertThat(result).isEqualTo(84);
    }

    @Test
    void shouldEvaluateWithType() {
        final var ctx = new StandardEvaluationContext();
        ctx.setVariable("flag", true);

        final var result = Expressions.evaluate("#flag", ctx, Boolean.class);

        assertThat(result).isTrue();
    }

    @Test
    void shouldThrowSecurityExceptionOnInvalidSyntax() {
        final var ctx = new StandardEvaluationContext();

        assertThatThrownBy(() -> Expressions.evaluate("invalid { syntax", ctx))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("RLS expression");
    }

    @Test
    void shouldCacheParsedExpressions() {
        final var ctx = new StandardEvaluationContext();
        ctx.setVariable("x", "hello");

        final var r1 = Expressions.evaluate("#x", ctx);
        final var r2 = Expressions.evaluate("#x", ctx);

        assertThat(r1).isEqualTo("hello");
        assertThat(r2).isEqualTo("hello");
    }

    @Test
    void shouldEvaluateStringConcatenation() {
        final var ctx = new StandardEvaluationContext();
        ctx.setVariable("name", "World");

        final var result = Expressions.evaluate("'Hello, ' + #name + '!'", ctx);

        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void shouldHandleNullInExpression() {
        final var ctx = new StandardEvaluationContext();
        ctx.setVariable("value", null);

        final var result = Expressions.evaluate("#value?.toString()", ctx);

        assertThat(result).isNull();
    }
}
