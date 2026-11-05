package io.github.khezyapp.a2a.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.FilePart;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Reduces SDK parts and artifacts to plain text.
 *
 * <p>A2A carries typed parts; hosts and language models usually need a string. This
 * extractor is deliberately lossy: binaries are represented by their file name (or mime
 * type), and structured data by its {@link Object#toString()} form.</p>
 */
public final class RemotePartExtractor {

    private RemotePartExtractor() {
    }

    /**
     * Extracts text from a single part.
     *
     * @param part the part, may be {@code null}
     * @return the extracted text, never {@code null}
     */
    public static String text(final Part<?> part) {
        if (part == null) {
            return "";
        }
        if (part instanceof TextPart textPart) {
            return Objects.isNull(textPart.text()) ? "" : textPart.text();
        }
        if (part instanceof DataPart dataPart) {
            return Objects.isNull(dataPart.data()) ? "" : String.valueOf(dataPart.data());
        }
        if (part instanceof FilePart filePart) {
            final var file = filePart.file();
            if (Objects.isNull(file)) {
                return "";
            }
            if (Objects.nonNull(file.name()) && !file.name().isBlank()) {
                return file.name();
            }
            return Objects.isNull(file.mimeType()) ? "" : file.mimeType();
        }
        return part.toString();
    }

    /**
     * Extracts text from a list of parts, joining non-blank values with newlines.
     *
     * @param parts the parts, may be {@code null}
     * @return the joined text, never {@code null}
     */
    public static String text(final List<? extends Part<?>> parts) {
        if (Objects.isNull(parts) || parts.isEmpty()) {
            return "";
        }
        final var values = new ArrayList<String>(parts.size());
        for (final var part : parts) {
            final var text = text(part);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return String.join("\n", values);
    }

    /**
     * Extracts text from an artifact's parts.
     *
     * @param artifact the artifact, may be {@code null}
     * @return the joined text, never {@code null}
     */
    public static String text(final Artifact artifact) {
        return Objects.isNull(artifact) ? "" : text(artifact.parts());
    }

    /**
     * Maps SDK artifacts to remote artifact views.
     *
     * @param artifacts the SDK artifacts, may be {@code null}
     * @return an immutable list of artifact views
     */
    public static List<RemoteArtifact> artifacts(final List<Artifact> artifacts) {
        if (Objects.isNull(artifacts) || artifacts.isEmpty()) {
            return List.of();
        }
        final var values = new ArrayList<RemoteArtifact>(artifacts.size());
        for (final var artifact : artifacts) {
            if (Objects.isNull(artifact)) {
                continue;
            }
            values.add(new RemoteArtifact(
                    artifact.name(),
                    artifact.description(),
                    text(artifact)));
        }
        return List.copyOf(values);
    }

    /**
     * Returns the first non-blank value.
     *
     * @param values the candidate values
     * @return the first non-blank value, or {@code null}
     */
    static String firstNonBlank(final String... values) {
        Objects.requireNonNull(values, "values must not be null");
        for (final var value : values) {
            if (Objects.nonNull(value) && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
