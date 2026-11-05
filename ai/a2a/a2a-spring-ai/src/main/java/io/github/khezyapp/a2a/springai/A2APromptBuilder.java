package io.github.khezyapp.a2a.springai;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.khezyapp.a2a.core.client.RemoteSkill;

/**
 * Default {@link A2APromptProvider}: renders a routing system prompt from the
 * discovered skills.
 *
 * <p>The tools are the primary discovery mechanism; this prompt nudges the model toward
 * the most specific tool, toward restating new information when continuing an exchange,
 * and toward {@code listRemoteAgents} when it is unsure. Instructions, header, per-skill
 * line and empty-skills prompt are all replaceable through {@link #builder()}.</p>
 *
 * <p>Skill lines support the {@code {agent}}, {@code {id}}, {@code {name}},
 * {@code {description}}, {@code {tags}} and {@code {examples}} placeholders. Substitution
 * is single-pass, so card text containing braces is never re-interpreted.</p>
 */
public final class A2APromptBuilder implements A2APromptProvider {

    /** Default routing instructions. */
    public static final String DEFAULT_INSTRUCTIONS = """
            You are a routing assistant with access to tools that delegate work to remote A2A agents.
            Choose the single most specific tool for the user's request and pass a complete task \
            description, including any context the remote agent needs.
            If a remote agent asks for clarification, relay the question to the user and call the same \
            tool again. When continuing a previous exchange, restate the new information; the client \
            automatically resumes the correct remote task.
            When unsure which agent exposes the right capability, call listRemoteAgents first.
            Present the remote agent's result to the user faithfully; do not invent results.
            """;

    /** Default header printed before the skill lines. */
    public static final String DEFAULT_SKILLS_HEADER = "Available skills:";

    /** Default per-skill line; {@code {description}} already carries its separator. */
    public static final String DEFAULT_SKILL_TEMPLATE = "- {agent} / {id}: {name}{description}";

    /** Default prompt when no skill is discovered. */
    public static final String DEFAULT_EMPTY_PROMPT =
            "You are a helpful assistant. No remote agents are currently available.";

    private final String instructions;
    private final String skillsHeader;
    private final String skillTemplate;
    private final String emptyPrompt;

    /**
     * Creates a builder with the default instructions.
     */
    public A2APromptBuilder() {
        this(builder());
    }

    private A2APromptBuilder(final Builder builder) {
        this.instructions = builder.instructions;
        this.skillsHeader = builder.skillsHeader;
        this.skillTemplate = builder.skillTemplate;
        this.emptyPrompt = builder.emptyPrompt;
    }

    /**
     * Returns a builder for a customized prompt.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String build(final List<RemoteSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return emptyPrompt;
        }
        final var prompt = new StringBuilder(256);
        if (hasText(instructions)) {
            prompt.append(instructions.stripTrailing()).append("\n\n");
        }
        if (hasText(skillsHeader)) {
            prompt.append(skillsHeader.strip()).append('\n');
        }
        for (final var skill : skills) {
            prompt.append(renderSkill(skill)).append('\n');
        }
        return prompt.toString();
    }

    private String renderSkill(final RemoteSkill skill) {
        return render(skillTemplate, Map.of(
                "agent", text(skill.agentName()),
                "id", text(skill.id()),
                "name", text(skill.name()),
                "description", descriptionSuffix(skill.description()),
                "tags", String.join(", ", skill.tags()),
                "examples", String.join(" | ", skill.examples())));
    }

    /**
     * Single-pass {@code {key}} substitution.
     *
     * <p>Unknown keys are kept verbatim so a typo is visible instead of silently dropping
     * text. Substituted values are never re-scanned.</p>
     */
    private static String render(final String template, final Map<String, String> values) {
        if (template == null) {
            return "";
        }
        final var rendered = new StringBuilder(template.length() + 64);
        var index = 0;
        while (index < template.length()) {
            final var character = template.charAt(index);
            if (character != '{') {
                rendered.append(character);
                index++;
                continue;
            }
            final var close = template.indexOf('}', index + 1);
            if (close < 0) {
                rendered.append(template, index, template.length());
                break;
            }
            final var key = template.substring(index + 1, close);
            final var value = values.get(key);
            if (value == null) {
                rendered.append(template, index, close + 1);
            } else {
                rendered.append(value);
            }
            index = close + 1;
        }
        return rendered.toString();
    }

    private static String descriptionSuffix(final String description) {
        return description == null || description.isBlank() ? "" : " - " + description;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String text(final String value) {
        return Objects.toString(value, "");
    }

    /**
     * Fluent customization; unset values keep the defaults.
     */
    public static final class Builder {

        private String instructions = DEFAULT_INSTRUCTIONS;
        private String skillsHeader = DEFAULT_SKILLS_HEADER;
        private String skillTemplate = DEFAULT_SKILL_TEMPLATE;
        private String emptyPrompt = DEFAULT_EMPTY_PROMPT;

        private Builder() {
        }

        /**
         * Replaces the routing instructions.
         *
         * @param value the new instructions
         * @return this builder
         */
        public Builder instructions(final String value) {
            this.instructions = value;
            return this;
        }

        /**
         * Replaces the header printed before the skill lines.
         *
         * @param value the new header; blank removes it
         * @return this builder
         */
        public Builder skillsHeader(final String value) {
            this.skillsHeader = value;
            return this;
        }

        /**
         * Replaces the per-skill line template.
         *
         * @param value the new template
         * @return this builder
         */
        public Builder skillTemplate(final String value) {
            this.skillTemplate = value;
            return this;
        }

        /**
         * Replaces the prompt used when no skill is discovered.
         *
         * @param value the new empty prompt
         * @return this builder
         */
        public Builder emptyPrompt(final String value) {
            this.emptyPrompt = value;
            return this;
        }

        /**
         * Builds the provider.
         *
         * @return the prompt builder
         */
        public A2APromptBuilder build() {
            return new A2APromptBuilder(this);
        }
    }
}
