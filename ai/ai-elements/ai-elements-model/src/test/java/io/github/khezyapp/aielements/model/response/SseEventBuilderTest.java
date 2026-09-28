package io.github.khezyapp.aielements.model.response;

import io.github.khezyapp.aielements.model.common.FinishReason;
import io.github.khezyapp.aielements.model.common.Usage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseEventBuilderTest {

    @Test
    @DisplayName("Should return an immutable ordered event list")
    void buildReturnsImmutableOrderedList() {
        final var builder = new SseEventBuilder();
        builder.start("m-1");
        builder.startStep();
        builder.text("0", "hello");

        final var events = builder.build();

        assertEquals(List.of(
                new SseEvent.Start("m-1"),
                new SseEvent.StartStep(),
                new SseEvent.TextStart("0"),
                new SseEvent.TextDelta("0", "hello"),
                new SseEvent.TextEnd("0")
        ), events);
        assertThrows(UnsupportedOperationException.class,
                () -> events.add(new SseEvent.StartStep()));
    }

    @Test
    @DisplayName("Should append a tool-output-error event")
    void toolOutputErrorAppendsEvent() {
        final var builder = new SseEventBuilder();
        builder.toolOutputError("t-1", "boom");

        assertEquals(List.of(new SseEvent.ToolOutputError("t-1", "boom")), builder.build());
    }

    @Test
    @DisplayName("Should concatenate wire format and end with the [DONE] sentinel")
    void buildWireFormatEndsWithDone() {
        final var builder = new SseEventBuilder();
        builder.start("m-1");
        builder.finish(FinishReason.STOP, Usage.empty());

        final var wire = builder.buildWireFormat();

        assertEquals(
                "data: {\"type\":\"start\",\"messageId\":\"m-1\"}\n\n"
                        + "data: {\"type\":\"finish-step\"}\n\n"
                        + "data: {\"type\":\"finish\",\"finishReason\":\"stop\","
                        + "\"messageMetadata\":{\"usage\":{\"inputTokens\":0,\"outputTokens\":0,"
                        + "\"totalTokens\":0}}}\n\n"
                        + "data: [DONE]\n\n",
                wire);
    }
}
