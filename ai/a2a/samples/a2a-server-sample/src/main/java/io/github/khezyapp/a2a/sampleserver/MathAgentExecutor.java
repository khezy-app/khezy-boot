package io.github.khezyapp.a2a.sampleserver;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.khezyapp.a2a.core.executor.AbstractA2AAgentExecutor;
import io.github.khezyapp.a2a.core.executor.AgentExecutionContext;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.stereotype.Component;

/**
 * Toy agent for local validation: parses {@code <int> <op> <int>} (e.g. "2 + 3") from the
 * incoming text part and answers with a completed status update whose message carries the
 * integer result. Deliberately no expression library and no LLM — the point is the A2A
 * plumbing, not the math.
 */
@Component
public class MathAgentExecutor extends AbstractA2AAgentExecutor {

    private static final String OP_ADD = "+";
    private static final String OP_SUBTRACT = "-";
    private static final String OP_MULTIPLY = "*";
    private static final String OP_DIVIDE = "/";
    private static final int INVALID_PARAMS_CODE = -32602;
    private static final int EXPRESSION_TOKENS = 3;

    @Override
    public EventKind execute(final AgentExecutionContext context) {
        final var incoming = context.incoming();
        final var expression = firstText(incoming)
                .orElseThrow(() -> invalidParams("expected one non-blank text part with an arithmetic expression"));
        final var taskId = resolveTaskId(context);
        final var contextId = resolveContextId(context, taskId);
        final var reply = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(evaluate(expression))))
                .taskId(taskId)
                .contextId(contextId)
                .build();
        return new TaskStatusUpdateEvent(
                taskId,
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, reply, OffsetDateTime.now()),
                contextId,
                Map.of());
    }

    /**
     * TaskStatusUpdateEvent rejects null ids, and the wire message may omit them — the
     * dispatcher's resolved identity lives on {@link AgentExecutionContext#currentTask()},
     * so read it there before falling back to the incoming message / fresh UUIDs.
     */
    private static String resolveTaskId(final AgentExecutionContext context) {
        final var fromTask = context.currentTask().map(Task::id);
        return fromTask.orElseGet(() -> orUuid(context.incoming().taskId()));
    }

    private static String resolveContextId(final AgentExecutionContext context,
                                           final String fallback) {
        return context.currentTask()
                .map(Task::contextId)
                .or(() -> Optional.ofNullable(context.incoming().contextId()).filter(id -> !id.isBlank()))
                .filter(id -> !id.isBlank())
                .orElse(fallback);
    }

    private static String orUuid(final String value) {
        return Objects.nonNull(value) && !value.isBlank() ? value : UUID.randomUUID().toString();
    }

    /**
     * Evaluates {@code <left> <op> <right>} with single-space-separated tokens; anything
     * else is rejected as an invalid-params A2A error.
     */
    private static String evaluate(final String expression) {
        final var tokens = expression.trim().split("\\s+");
        if (tokens.length != EXPRESSION_TOKENS) {
            throw invalidParams("expected '<int> <op> <int>' but got: " + expression);
        }
        final var left = parseOperand(tokens[0]);
        final var right = parseOperand(tokens[2]);
        return switch (tokens[1]) {
            case OP_ADD -> String.valueOf(left + right);
            case OP_SUBTRACT -> String.valueOf(left - right);
            case OP_MULTIPLY -> String.valueOf((long) left * right);
            case OP_DIVIDE -> divide(left, right);
            default -> throw invalidParams("unsupported operator: " + tokens[1]);
        };
    }

    private static int parseOperand(final String token) {
        try {
            return Integer.parseInt(token);
        } catch (final NumberFormatException e) {
            throw invalidParams("operand is not an integer: " + token);
        }
    }

    private static String divide(final int left,
                                 final int right) {
        if (right == 0) {
            throw invalidParams("division by zero");
        }
        return String.valueOf(left / right);
    }

    private static Optional<String> firstText(final Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .filter(text -> !text.isBlank())
                .findFirst();
    }

    private static A2AError invalidParams(final String message) {
        return new A2AError(INVALID_PARAMS_CODE, message, Map.of());
    }
}
