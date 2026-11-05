package io.github.khezyapp.a2a.springai;

/**
 * Input of one skill tool call: a free-text task description.
 *
 * <p>Agent cards do not describe per-skill input schemas, so every skill accepts the
 * same single-argument shape. A2A ids are intentionally absent: they are opaque,
 * server-generated, and travel through {@link A2AToolContextKeys} instead.</p>
 *
 * @param task the complete task description and context
 */
public record SkillInput(String task) {
}
