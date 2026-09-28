package io.github.khezyapp.aielements.model.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatRequestJsonRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should round-trip a ChatRequest with every static MessagePart type")
    void shouldRoundTripChatRequestWithAllPartTypes() throws Exception {
        final var parts = new ArrayList<MessagePart>();
        parts.add(new TextPart("text", "hello"));
        parts.add(new ReasoningPart("reasoning", "thinking"));
        parts.add(new SourceUrlPart("source-url", "s-1", "http://example.com", "Example", null));
        parts.add(new SourceDocumentPart("source-document", "d-1", "application/pdf",
                "Doc", "doc.pdf", null));
        parts.add(new FilePart("file", "image/png", "photo.png",
                "data:image/png;base64,YWJj", null));
        parts.add(new ReasoningFilePart("reasoning-file", "image/png",
                "data:image/png;base64,YWJj", null));
        parts.add(new StepStartPart("step-start"));
        parts.add(ToolPart.call("call-1", "getWeather", Map.of("city", "Siem Reap")));
        parts.add(new CustomPart("custom", "anthropic.thinking", null));
        final var message = new ChatMessage("m-1", "assistant", "", parts);
        final var request = new ChatRequest(
                "req-1", List.of(message), "submit-message", null, "deepseek-chat");

        final var json = objectMapper.writeValueAsString(request);
        final var decoded = objectMapper.readValue(json, ChatRequest.class);

        assertEquals("req-1", decoded.id());
        assertEquals("deepseek-chat", decoded.model());
        final var decodedParts = decoded.messages().get(0).parts();
        assertInstanceOf(TextPart.class, decodedParts.get(0));
        assertInstanceOf(ReasoningPart.class, decodedParts.get(1));
        assertInstanceOf(SourceUrlPart.class, decodedParts.get(2));
        assertInstanceOf(SourceDocumentPart.class, decodedParts.get(3));
        assertInstanceOf(FilePart.class, decodedParts.get(4));
        assertInstanceOf(ReasoningFilePart.class, decodedParts.get(5));
        assertInstanceOf(StepStartPart.class, decodedParts.get(6));
        assertInstanceOf(ToolPart.class, decodedParts.get(7));
        assertInstanceOf(CustomPart.class, decodedParts.get(8));
        assertEquals("source-url", decodedParts.get(2).type());
        assertEquals("dynamic-tool", decodedParts.get(7).type());
        assertEquals("custom", decodedParts.get(8).type());
    }

    @Test
    @DisplayName("Should bind the visible type discriminator to the part type property")
    void shouldBindTypeDiscriminator() throws Exception {
        final var part = objectMapper.readValue("{\"type\":\"text\",\"text\":\"hi\"}",
                MessagePart.class);

        assertEquals("text", part.type());
    }

    @Test
    @DisplayName("Should round-trip a dynamic-tool part with approval and tool metadata")
    void shouldRoundTripDynamicToolPart() throws Exception {
        final var tool = new ToolPart("dynamic-tool", "getWeather", "call-1",
                "approval-requested", Map.of("city", "Siem Reap"), null, null, true,
                "Weather", Map.of("id", "ap-1", "approved", false),
                Map.of("source", "server"));
        final var json = objectMapper.writeValueAsString(tool);

        final var decoded = (ToolPart) objectMapper.readValue(json, MessagePart.class);

        assertEquals(tool, decoded);
    }

    @Test
    @DisplayName("Should preserve unknown tool-<name> and data-<name> parts verbatim")
    void shouldPreserveUnknownParts() throws Exception {
        final var toolJson = "{\"type\":\"tool-getWeather\",\"toolCallId\":\"c1\","
                + "\"state\":\"output-available\",\"input\":{\"city\":\"Siem Reap\"},"
                + "\"output\":{\"temp\":33}}";
        final var dataJson = "{\"type\":\"data-plan\",\"id\":\"p1\",\"data\":{\"step\":1}}";

        final var toolPart = objectMapper.readValue(toolJson, MessagePart.class);
        final var dataPart = objectMapper.readValue(dataJson, MessagePart.class);

        assertInstanceOf(UnknownPart.class, toolPart);
        assertInstanceOf(UnknownPart.class, dataPart);
        assertEquals("tool-getWeather", toolPart.type());
        assertEquals("data-plan", dataPart.type());
        assertEquals("c1", ((UnknownPart) toolPart).properties().get("toolCallId"));

        final var reEncoded = objectMapper.writeValueAsString(toolPart);
        assertEquals(toolJson, reEncoded);
    }

    @Test
    @DisplayName("Should tolerate AI SDK UI-message extra fields")
    void shouldTolerateExtraFields() throws Exception {
        final var json = "{\"id\":\"c1\",\"trigger\":\"submit-message\",\"messageId\":null,"
                + "\"model\":\"deepseek-chat\",\"extra\":123,\"messages\":[{\"id\":\"m1\","
                + "\"role\":\"user\",\"metadata\":{\"foo\":\"bar\"},"
                + "\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}";

        final var decoded = objectMapper.readValue(json, ChatRequest.class);

        assertEquals("c1", decoded.id());
        assertEquals("deepseek-chat", decoded.model());
        assertEquals(1, decoded.messages().size());
        assertEquals("user", decoded.messages().get(0).role());
        assertEquals("hi", ((TextPart) decoded.messages().get(0).parts().get(0)).text());
    }

    @Test
    @DisplayName("Should not expose a separate tool-result part in the MessagePart union")
    void shouldNotContainToolResultPart() {
        final var permitted = MessagePart.class.getPermittedSubclasses();

        for (final var type : permitted) {
            if ("ToolResultPart".equals(type.getSimpleName())
                    || "ToolInvocationPart".equals(type.getSimpleName())) {
                throw new AssertionError("Legacy tool part must not be a MessagePart type");
            }
        }
        assertNotNull(permitted);
    }
}
