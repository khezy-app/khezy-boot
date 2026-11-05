package io.github.khezyapp.a2a.springai;

import java.util.Objects;

import io.github.khezyapp.a2a.core.client.RemoteArtifact;
import io.github.khezyapp.a2a.core.client.SendOutcome;

/**
 * Renders a {@link SendOutcome} as compact JSON for a tool-calling model.
 *
 * <p>The shape is deliberately small and stable: {@code agent}, {@code skill},
 * {@code state}, {@code message}, {@code error} and {@code artifacts} (each with
 * {@code name}, {@code description} and {@code text}). Empty fields are omitted.
 * A2A ids are never printed — correlation stays in the client, not in the model's
 * context.</p>
 *
 * <p>Free-text values longer than {@code maxChars} are truncated with a marker so one
 * huge artifact cannot exhaust the model's context window.</p>
 */
public final class A2AResultFormatter {

    /** Default per-field character limit. */
    public static final int DEFAULT_MAX_CHARS = 8000;

    private final int maxChars;

    /**
     * Creates a formatter with the default limit.
     */
    public A2AResultFormatter() {
        this(DEFAULT_MAX_CHARS);
    }

    /**
     * Creates a formatter.
     *
     * @param maxChars the per-text-field character limit; non-positive values fall back
     *                 to {@link #DEFAULT_MAX_CHARS}
     */
    public A2AResultFormatter(final int maxChars) {
        this.maxChars = maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
    }

    /**
     * Returns the configured per-text-field character limit.
     *
     * @return the limit
     */
    public int maxChars() {
        return maxChars;
    }

    /**
     * Formats one outcome as compact JSON.
     *
     * @param outcome the outcome to format
     * @return the JSON document
     */
    public String format(final SendOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        final var json = new StringBuilder(128);
        json.append('{');
        var first = true;
        first = append(json, first, "agent", outcome.agent().name());
        first = append(json, first, "skill", outcome.skill() == null ? null : outcome.skill().id());
        first = append(json, first, "state", outcome.state() == null ? null : outcome.state().name());
        first = append(json, first, "message", truncate(outcome.message()));
        first = append(json, first, "error", truncate(outcome.error()));
        if (!outcome.artifacts().isEmpty()) {
            if (!first) {
                json.append(',');
            }
            appendArtifacts(json, outcome);
        }
        json.append('}');
        return json.toString();
    }

    /**
     * Escapes and quotes one JSON string value.
     *
     * @param value the raw value
     * @return the quoted JSON string
     */
    static String jsonString(final String value) {
        return '"' + escape(value) + '"';
    }

    private void appendArtifacts(final StringBuilder json, final SendOutcome outcome) {
        json.append("\"artifacts\":[");
        var first = true;
        for (final RemoteArtifact artifact : outcome.artifacts()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            var firstField = true;
            firstField = append(json, firstField, "name", artifact.name());
            firstField = append(json, firstField, "description", artifact.description());
            append(json, firstField, "text", truncate(artifact.text()));
            json.append('}');
        }
        json.append(']');
    }

    private static boolean append(final StringBuilder json, final boolean first,
                                  final String name, final String value) {
        if (value == null || value.isEmpty()) {
            return first;
        }
        if (!first) {
            json.append(',');
        }
        json.append(jsonString(name)).append(':').append(jsonString(value));
        return false;
    }

    private String truncate(final String value) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...[truncated " + (value.length() - maxChars) + " chars]";
    }

    private static String escape(final String value) {
        final var escaped = new StringBuilder(value.length() + 16);
        for (var index = 0; index < value.length(); index++) {
            final var character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
