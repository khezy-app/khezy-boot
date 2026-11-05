package io.github.khezyapp.a2a.core.client;

/**
 * Text view of an SDK {@link org.a2aproject.sdk.spec.Artifact}.
 *
 * @param name the artifact name, or {@code null}
 * @param description the artifact description, or {@code null}
 * @param text the extracted text, never {@code null}
 */
public record RemoteArtifact(String name, String description, String text) {

    /**
     * Returns whether the artifact carries text.
     *
     * @return {@code true} when the text is non-blank
     */
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
