package io.github.khezyapp.a2a.springai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import io.github.khezyapp.a2a.core.client.A2AConnector;
import io.github.khezyapp.a2a.core.client.RemoteSkill;
import io.github.khezyapp.a2a.core.client.SendOutcome;
import io.github.khezyapp.a2a.coredef.client.RemoteAgentRegistry;

/**
 * Skill-as-tool: one {@link ToolCallback} per discovered remote skill.
 *
 * <p>Names and descriptions come from the agent card, so registering an agent — or adding
 * a skill to its card — makes the capability visible to the model without code changes.
 * {@link #toolCallbacks()} builds from the registry's skills once and caches the result;
 * {@link #toolCallbacks(List)} builds an explicit, uncached set (for a per-session
 * subset).</p>
 *
 * <p>Invocation resolves the agent by name at call time, delegates the actual send to
 * {@link A2AConnector} — which resumes interrupted tasks per conversation — and formats
 * the outcome as compact JSON. The conversation id is read from the Spring AI
 * {@code ToolContext}; it is never a model argument.</p>
 */
public final class A2ASkillToolFactory {

    private static final int MAX_TOOL_NAME_LENGTH = 64;

    private static final String SKILL_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "task": {
                  "type": "string",
                  "description": "Complete task description and context. When continuing an \
            exchange, restate the new information."
                }
              },
              "required": ["task"],
              "additionalProperties": false
            }
            """;

    private final RemoteAgentRegistry registry;
    private final A2AConnector connector;
    private final A2AResultFormatter formatter;
    private volatile List<ToolCallback> cached;

    /**
     * Creates a factory.
     *
     * @param registry the remote agent registry
     * @param connector the conversation-aware connector
     * @param formatter the result formatter
     */
    public A2ASkillToolFactory(final RemoteAgentRegistry registry, final A2AConnector connector,
                               final A2AResultFormatter formatter) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.connector = Objects.requireNonNull(connector, "connector must not be null");
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
    }

    /**
     * Returns one tool per skill currently known to the registry, built once.
     *
     * @return the immutable tool list
     */
    public List<ToolCallback> toolCallbacks() {
        var result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = build(registry.skills());
                    cached = result;
                }
            }
        }
        return result;
    }

    /**
     * Builds tools for an explicit skill list; the result is never cached.
     *
     * @param skills the skills to expose
     * @return the immutable tool list
     */
    public List<ToolCallback> toolCallbacks(final List<RemoteSkill> skills) {
        return build(skills);
    }

    private List<ToolCallback> build(final List<RemoteSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        final var tools = new ArrayList<ToolCallback>(skills.size());
        final var usedNames = new HashSet<String>();
        for (final var skill : skills) {
            final var name = uniqueName(usedNames, toolName(skill.agentName(), skill.id()));
            tools.add(FunctionToolCallback
                    .builder(name, (SkillInput input, ToolContext context) -> invoke(skill, input, context))
                    .description(describe(skill))
                    .inputType(SkillInput.class)
                    .inputSchema(SKILL_INPUT_SCHEMA)
                    .build());
        }
        return List.copyOf(tools);
    }

    private String invoke(final RemoteSkill skill, final SkillInput input, final ToolContext context) {
        final var task = input == null || input.task() == null ? "" : input.task();
        final var agent = registry.find(skill.agentName()).orElse(null);
        if (agent == null) {
            return A2AToolErrors.unknownAgent(skill.agentName(), registry.agents());
        }
        final var conversationId = conversationId(context);
        if (conversationId == null) {
            return A2AToolErrors.missingConversationId();
        }
        final List<Part<?>> parts = List.of(new TextPart(task));
        final SendOutcome outcome = connector.invoke(agent, parts, skill.id(), conversationId);
        return formatter.format(outcome);
    }

    /**
     * Reads the host conversation id from the tool context.
     *
     * @param context the Spring AI tool context, possibly {@code null}
     * @return the conversation id, or {@code null} when absent or blank
     */
    static String conversationId(final ToolContext context) {
        if (context == null || context.getContext() == null) {
            return null;
        }
        final var value = context.getContext().get(A2AToolContextKeys.CONVERSATION_ID);
        return value instanceof String id && !id.isBlank() ? id : null;
    }

    private static String describe(final RemoteSkill skill) {
        final var description = new StringBuilder(128);
        description.append("Invoke the '").append(text(skill.name()))
                .append("' skill on remote A2A agent '").append(text(skill.agentName())).append("'. ");
        if (hasText(skill.description())) {
            description.append(skill.description()).append(' ');
        }
        if (!skill.tags().isEmpty()) {
            description.append("Tags: ").append(String.join(", ", skill.tags())).append(". ");
        }
        if (!skill.examples().isEmpty()) {
            description.append("Examples: ").append(String.join(" | ", skill.examples())).append(". ");
        }
        description.append("Provide a complete task description with all context the agent needs. ")
                .append("When continuing an exchange, restate the new information; ")
                .append("the same remote task is resumed automatically.");
        return description.toString();
    }

    private static String toolName(final String agentName, final String skillId) {
        final var raw = sanitize(agentName) + "__" + sanitize(skillId);
        return raw.length() <= MAX_TOOL_NAME_LENGTH ? raw : raw.substring(0, MAX_TOOL_NAME_LENGTH);
    }

    private static String sanitize(final String value) {
        if (value == null || value.isBlank()) {
            return "unnamed";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String uniqueName(final Set<String> used, final String candidate) {
        var name = candidate;
        var suffix = 2;
        while (!used.add(name)) {
            final var tail = "_" + suffix++;
            final var maxBase = MAX_TOOL_NAME_LENGTH - tail.length();
            name = (candidate.length() > maxBase ? candidate.substring(0, maxBase) : candidate) + tail;
        }
        return name;
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    private static String text(final String value) {
        return value == null ? "" : value;
    }
}
