package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ChatRequestJsonRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should round-trip a ChatRequest with every MessagePart type")
    void shouldRoundTripChatRequestWithAllPartTypes() throws Exception {
        final var parts = new ArrayList<MessagePart>();
        parts.add(new TextPart("text", "hello"));
        parts.add(new ReasoningPart("reasoning", "thinking"));
        parts.add(new SourceUrlPart("source-url", "http://example.com", "Example"));
        parts.add(new FilePart("file", "notes.txt", "text/plain", "file-data"));
        parts.add(new ToolInvocationPart(
                "tool-invocation", "call-1", "search", "call", Map.of("q", "x"), null));
        parts.add(new StepStartPart("step-start"));
        parts.add(new StepFinishPart("step-finish"));
        final var message = new ChatMessage("m-1", "assistant", "", parts);
        final var request = new ChatRequest("req-1", List.of(message), "submit-message", null);

        final var json = objectMapper.writeValueAsString(request);
        final var decoded = objectMapper.readValue(json, ChatRequest.class);

        assertEquals(request.id(), decoded.id());
        assertEquals(request.messages().get(0).id(), decoded.messages().get(0).id());
        assertInstanceOf(TextPart.class, decoded.messages().get(0).parts().get(0));
        assertInstanceOf(ReasoningPart.class, decoded.messages().get(0).parts().get(1));
        assertInstanceOf(SourceUrlPart.class, decoded.messages().get(0).parts().get(2));
        assertInstanceOf(FilePart.class, decoded.messages().get(0).parts().get(3));
        final var tool = decoded.messages().get(0).parts().get(4);
        assertInstanceOf(ToolInvocationPart.class, tool);
        assertEquals("call-1", ((ToolInvocationPart) tool).toolCallId());
        assertInstanceOf(StepStartPart.class, decoded.messages().get(0).parts().get(5));
        assertInstanceOf(StepFinishPart.class, decoded.messages().get(0).parts().get(6));
    }

    @Test
    @DisplayName("Should discriminate the concrete part type from the type field")
    void shouldDiscriminateOnTypeField() throws Exception {
        final var json = "{\"type\":\"tool-invocation\",\"toolCallId\":\"c1\","
                + "\"toolName\":\"search\",\"state\":\"call\",\"args\":{},\"result\":null}";

        final var part = objectMapper.readValue(json, MessagePart.class);

        assertInstanceOf(ToolInvocationPart.class, part);
        assertEquals("c1", ((ToolInvocationPart) part).toolCallId());
    }

    @Test
    @DisplayName("Should not expose ToolResultPart in the MessagePart union")
    void shouldNotContainToolResultPart() {
        final var permitted = MessagePart.class.getPermittedSubclasses();

        assertEquals(7, permitted.length);
        for (final var type : permitted) {
            if ("ToolResultPart".equals(type.getSimpleName())) {
                throw new AssertionError("ToolResultPart must not be a MessagePart type");
            }
        }
    }
}
