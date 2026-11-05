package io.github.khezyapp.a2a.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.FilePart;
import org.a2aproject.sdk.spec.FileWithBytes;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class RemotePartExtractorTest {

    @Test
    void textPartShouldReturnText() {
        assertEquals("hello", RemotePartExtractor.text(new TextPart("hello")));
    }

    @Test
    void dataPartShouldReturnStringValue() {
        assertEquals("payload", RemotePartExtractor.text(new DataPart("payload")));
    }

    @Test
    void dataPartShouldStringifyStructuredValue() {
        assertEquals(String.valueOf(Map.of("a", 1)),
                RemotePartExtractor.text(new DataPart(Map.of("a", 1))));
    }

    @Test
    void filePartShouldPreferFileName() {
        final var part = new FilePart(new FileWithBytes("text/plain", "report.txt", "aGk="));

        assertEquals("report.txt", RemotePartExtractor.text(part));
    }

    @Test
    void nullPartShouldBeEmpty() {
        assertEquals("", RemotePartExtractor.text((Part<?>) null));
    }

    @Test
    void unknownPartShouldUseToString() {
        assertEquals("unknown", RemotePartExtractor.text(new UnknownPart()));
    }

    @Test
    void listShouldJoinNonBlankTexts() {
        final var parts = List.<Part<?>>of(
                new TextPart("one"),
                new TextPart(" "),
                new DataPart("two"));

        assertEquals("one\ntwo", RemotePartExtractor.text(parts));
    }

    @Test
    void nullOrEmptyListShouldBeEmpty() {
        assertEquals("", RemotePartExtractor.text((List<Part<?>>) null));
        assertEquals("", RemotePartExtractor.text(List.of()));
    }

    @Test
    void artifactTextShouldJoinParts() {
        final var artifact = Artifact.builder()
                .artifactId("art-1")
                .parts(List.of(new TextPart("line 1"), new TextPart("line 2")))
                .build();

        assertEquals("line 1\nline 2", RemotePartExtractor.text(artifact));
        assertEquals("", RemotePartExtractor.text((Artifact) null));
    }

    @Test
    void artifactsShouldMapNameDescriptionAndText() {
        final var artifact = Artifact.builder()
                .artifactId("art-1")
                .name("summary")
                .description("a summary")
                .parts(List.of(new TextPart("the text")))
                .build();

        final var mapped = RemotePartExtractor.artifacts(List.of(artifact));

        assertEquals(1, mapped.size());
        assertEquals("summary", mapped.get(0).name());
        assertEquals("a summary", mapped.get(0).description());
        assertEquals("the text", mapped.get(0).text());
        assertTrue(mapped.get(0).hasText());
    }

    @Test
    void artifactsShouldBeEmptyForNull() {
        assertTrue(RemotePartExtractor.artifacts(null).isEmpty());
    }

    private static final class UnknownPart implements Part<Object> {

        @Override
        public String toString() {
            return "unknown";
        }
    }
}
