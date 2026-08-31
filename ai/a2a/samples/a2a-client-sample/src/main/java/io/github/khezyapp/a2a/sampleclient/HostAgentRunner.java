package io.github.khezyapp.a2a.sampleclient;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.khezyapp.a2a.coredef.client.SdkA2ARemoteAgentClient;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Host-agent validation loop: discover the remote agent card, send "2 + 3" over the
 * {@link SdkA2ARemoteAgentClient} and log the reply. Failures are logged, never thrown —
 * a sample must not crash the boot process; read the ERROR line to see what went wrong.
 */
@Slf4j
@Component
public class HostAgentRunner implements CommandLineRunner {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    private static final String MATH_EXPRESSION = "2 + 3";

    private final String serverUrl;

    public HostAgentRunner(
            @Value("${a2a.server.url:" + DEFAULT_SERVER_URL + "}") final String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public void run(final String... args) {
        final var baseUrl = URI.create(serverUrl);
        try (var client = SdkA2ARemoteAgentClient.forJsonRpc(baseUrl, TIMEOUT)) {
            final var card = client.discover(baseUrl);
            log.info("Discovered remote agent '{}' v{}", card.name(), card.version());
            final var reply = client.sendMessage(userMessage(MATH_EXPRESSION), TIMEOUT);
            log.info("Remote math agent replied: {}", describe(reply));
        } catch (final Exception e) {
            log.error("A2A host agent call failed for {}: {}", serverUrl,
                    Objects.requireNonNullElse(e.getMessage(), e.getClass().getName()), e);
        }
    }

    private static Message userMessage(final String text) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart(text)))
                .build();
    }

    /**
     * Renders whichever terminal event came back: a reply message, a completed status
     * update carrying one, or a bare task.
     */
    private static String describe(final EventKind event) {
        if (event instanceof final Message message) {
            return "message \"" + firstText(message).orElse("<no text>") + "\"";
        }
        if (event instanceof final TaskStatusUpdateEvent update
                && Objects.nonNull(update.status().message())) {
            return update.status().state() + " \""
                    + firstText(update.status().message()).orElse("<no text>") + "\"";
        }
        if (event instanceof final Task task) {
            return "task " + task.id() + " state=" + task.status().state();
        }
        return event.getClass().getSimpleName();
    }

    private static Optional<String> firstText(final Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .filter(text -> !text.isBlank())
                .findFirst();
    }
}
