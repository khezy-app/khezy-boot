package io.github.khezyapp.a2a.core.client.invoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.ClientFixtures;
import io.github.khezyapp.a2a.core.client.RemoteAgent;

class SendRequestTest {

    private final RemoteAgent agent = ClientFixtures.agent();

    @Test
    void builderShouldDefaultOptionsAndCollections() {
        final var request = SendRequest.builder().agent(agent).build();

        assertTrue(request.parts().isEmpty());
        assertTrue(request.referenceTaskIds().isEmpty());
        assertTrue(request.extensions().isEmpty());
        assertTrue(request.paramsMetadata().isEmpty());
        assertTrue(request.messageMetadata().isEmpty());
        assertEquals(SendOptions.defaults(), request.options());
        assertNull(request.skillId());
        assertNull(request.contextId());
        assertNull(request.taskId());
        assertNull(request.tenant());
        assertNull(request.configuration());
        assertFalse(request.hasExplicitTaskRef());
    }

    @Test
    void constructorShouldDefensivelyCopyCollections() {
        final var parts = new ArrayList<org.a2aproject.sdk.spec.Part<?>>();
        parts.add(new TextPart("hello"));
        final var references = new ArrayList<>(List.of("ref-1"));
        final var extensions = new ArrayList<>(List.of("ext-1"));
        final var paramsMetadata = new LinkedHashMap<String, Object>();
        paramsMetadata.put("a", 1);
        final var messageMetadata = new LinkedHashMap<String, Object>();
        messageMetadata.put("b", 2);

        final var request = new SendRequest(agent, parts, "echo", null, null, references, "tenant-1",
                null, paramsMetadata, messageMetadata, extensions, SendOptions.defaults());

        parts.clear();
        references.add("ref-2");
        extensions.add("ext-2");
        paramsMetadata.put("a", 99);
        messageMetadata.put("b", 99);

        assertEquals(1, request.parts().size());
        assertEquals(List.of("ref-1"), request.referenceTaskIds());
        assertEquals(List.of("ext-1"), request.extensions());
        assertEquals(1, request.paramsMetadata().get("a"));
        assertEquals(2, request.messageMetadata().get("b"));
        assertThrows(UnsupportedOperationException.class, () -> request.parts().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> request.paramsMetadata().put("c", 3));
    }

    @Test
    void explicitContextShouldBeTracked() {
        final var builder = SendRequest.builder().agent(agent).contextId("ctx-1");

        assertTrue(builder.hasExplicitTaskRef());
        assertTrue(builder.build().hasExplicitTaskRef());
    }

    @Test
    void explicitTaskShouldBeTracked() {
        final var builder = SendRequest.builder().agent(agent).taskId("task-1");

        assertTrue(builder.hasExplicitTaskRef());
        assertTrue(builder.build().hasExplicitTaskRef());
    }

    @Test
    void blankIdsShouldNotCountAsExplicit() {
        final var builder = SendRequest.builder().agent(agent).contextId("  ").taskId("");

        assertFalse(builder.hasExplicitTaskRef());
        assertNull(builder.build().contextId());
        assertNull(builder.build().taskId());
    }

    @Test
    void toBuilderShouldRoundTripEveryComponent() {
        final var configuration = MessageSendConfiguration.builder()
                .acceptedOutputModes(List.of("text"))
                .historyLength(5)
                .returnImmediately(false)
                .build();
        final var options = SendOptions.builder().timeout(Duration.ofSeconds(3)).build();
        final var original = SendRequest.builder()
                .agent(agent)
                .parts(List.of(new TextPart("hello")))
                .skillId("echo")
                .contextId("ctx-1")
                .taskId("task-1")
                .referenceTaskIds(List.of("ref-1"))
                .tenant("tenant-1")
                .configuration(configuration)
                .paramsMetadata(Map.of("a", 1))
                .messageMetadata(Map.of("b", 2))
                .extensions(List.of("ext-1"))
                .options(options)
                .build();

        final var copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertTrue(copy.hasExplicitTaskRef());
    }

    @Test
    void partShouldAppendToParts() {
        final var request = SendRequest.builder()
                .agent(agent)
                .part(new TextPart("one"))
                .part(new TextPart("two"))
                .build();

        assertEquals(2, request.parts().size());
    }

    @Test
    void buildShouldRejectMissingAgent() {
        assertThrows(NullPointerException.class, () -> SendRequest.builder().build());
    }
}
