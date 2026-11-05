package io.github.khezyapp.a2a.core.client;

import java.util.List;
import java.util.Objects;

import org.a2aproject.sdk.spec.AgentSkill;

/**
 * Skill metadata together with the agent that advertises it.
 *
 * <p>This is a convenience view used by client hosts and LLM tools; the SDK
 * {@link AgentSkill} remains the source of truth.</p>
 *
 * @param agentName the owning agent name
 * @param agentUrl the owning agent base URL
 * @param id the skill id
 * @param name the skill name
 * @param description the skill description
 * @param tags the skill tags
 * @param examples the skill examples
 * @param inputModes supported input modes
 * @param outputModes supported output modes
 */
public record RemoteSkill(String agentName,
                          String agentUrl,
                          String id,
                          String name,
                          String description,
                          List<String> tags,
                          List<String> examples,
                          List<String> inputModes,
                          List<String> outputModes) {

    /**
     * Creates a skill view with defensive list copies.
     */
    public RemoteSkill {
        Objects.requireNonNull(agentName, "agentName must not be null");
        tags = copy(tags);
        examples = copy(examples);
        inputModes = copy(inputModes);
        outputModes = copy(outputModes);
    }

    /**
     * Maps an SDK skill to a remote skill view.
     *
     * @param agentName the owning agent name
     * @param agentUrl the owning agent base URL
     * @param skill the SDK skill
     * @return the remote skill view
     */
    public static RemoteSkill from(final String agentName,
                                   final String agentUrl,
                                   final AgentSkill skill) {
        Objects.requireNonNull(skill, "skill must not be null");
        return new RemoteSkill(
                agentName,
                agentUrl,
                skill.id(),
                skill.name(),
                skill.description(),
                skill.tags(),
                skill.examples(),
                skill.inputModes(),
                skill.outputModes());
    }

    private static List<String> copy(final List<String> values) {
        return Objects.isNull(values) ? List.of() : List.copyOf(values);
    }
}
