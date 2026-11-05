package io.github.khezyapp.a2a.sampleserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.khezyapp.a2a.core.server.A2ARequest;
import io.github.khezyapp.a2a.core.server.AbstractA2AAgentExecutor;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.stereotype.Component;

/**
 * Toy agent for local validation: parses {@code <int> <op> <int>} (for example
 * {@code "2 + 3"}) and answers with an artifact plus a completion message.
 *
 * <p>The agent never sees task or context ids: {@link A2ARequest} owns that ceremony.
 * When only part of an expression arrives, the agent asks for the missing operand and
 * reconstructs the expression from the task history on the next turn.</p>
 */
@Component
public class MathAgentExecutor extends AbstractA2AAgentExecutor {

    private static final String OP_ADD = "+";
    private static final String OP_SUBTRACT = "-";
    private static final String OP_MULTIPLY = "*";
    private static final String OP_DIVIDE = "/";
    private static final int EXPRESSION_TOKENS = 3;

    @Override
    protected void onMessage(final A2ARequest request) {
        final var expression = expression(request);
        if (expression.isBlank()) {
            request.requireInput("What should I calculate? Use '<number> <op> <number>', for example '2 + 3'.");
            return;
        }
        final var tokens = expression.split("\\s+");
        if (tokens.length < EXPRESSION_TOKENS) {
            request.requireInput("What is the missing operand?");
            return;
        }
        try {
            final var result = evaluate(tokens);
            request.artifact("result", result);
            request.complete(result);
        } catch (final InvalidExpressionException e) {
            request.fail(e.getMessage());
        }
    }

    /**
     * Rebuilds the expression from the previous user turns plus the current input.
     *
     * <p>On a resumed {@code INPUT_REQUIRED} task the task history holds {@code "2 +"} and
     * the incoming message holds {@code "3"}; joining them yields {@code "2 + 3"} without
     * the agent touching task ids.</p>
     */
    private static String expression(final A2ARequest request) {
        final var pieces = new ArrayList<String>();
        final var task = request.currentTask().orElse(null);
        if (Objects.nonNull(task) && Objects.nonNull(task.history())) {
            task.history().forEach(message -> collectUserText(message, pieces));
        }
        if (!alreadyInHistory(task, request.incoming())) {
            collectUserText(request.incoming(), pieces);
        }
        return String.join(" ", pieces).strip();
    }

    private static boolean alreadyInHistory(final Task task,
                                            final Message incoming) {
        if (Objects.isNull(task) || Objects.isNull(task.history()) ||
                Objects.isNull(incoming) || Objects.isNull(incoming.messageId())) {
            return false;
        }
        return task.history().stream()
                .anyMatch(message -> incoming.messageId().equals(message.messageId()));
    }

    private static void collectUserText(final Message message,
                                        final List<String> target) {
        if (Objects.isNull(message) || message.role() != Message.Role.ROLE_USER) {
            return;
        }
        for (final Part<?> part : message.parts()) {
            if (part instanceof final TextPart text && Objects.nonNull(text.text()) && !text.text().isBlank()) {
                target.add(text.text().strip());
            }
        }
    }

    private static String evaluate(final String[] tokens) {
        if (tokens.length != EXPRESSION_TOKENS) {
            throw new InvalidExpressionException("Expected '<number> <op> <number>' but got: "
                    + String.join(" ", tokens));
        }
        final var left = parseOperand(tokens[0]);
        final var right = parseOperand(tokens[2]);
        return switch (tokens[1]) {
            case OP_ADD -> String.valueOf(left + right);
            case OP_SUBTRACT -> String.valueOf(left - right);
            case OP_MULTIPLY -> String.valueOf((long) left * right);
            case OP_DIVIDE -> divide(left, right);
            default -> throw new InvalidExpressionException("Unsupported operator: " + tokens[1]);
        };
    }

    private static int parseOperand(final String token) {
        try {
            return Integer.parseInt(token);
        } catch (final NumberFormatException e) {
            throw new InvalidExpressionException("Operand is not an integer: " + token);
        }
    }

    private static String divide(final int left,
                                 final int right) {
        if (right == 0) {
            throw new InvalidExpressionException("Division by zero");
        }
        return String.valueOf(left / right);
    }

    /**
     * Signals a malformed expression to the request handler.
     */
    private static final class InvalidExpressionException extends RuntimeException {

        private InvalidExpressionException(final String message) {
            super(message);
        }
    }
}
