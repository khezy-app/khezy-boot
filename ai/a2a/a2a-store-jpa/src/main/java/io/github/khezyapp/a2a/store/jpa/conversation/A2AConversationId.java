package io.github.khezyapp.a2a.store.jpa.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite identity of a conversation entry: the host conversation id plus the agent
 * and skill selected for that turn.
 *
 * <p>An empty skill id means "no skill selected"; it is stored as an empty string rather
 * than {@code null} so the primary key is stable.</p>
 */
@Embeddable
public class A2AConversationId implements Serializable {

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "skill_id", nullable = false)
    private String skillId;

    /**
     * Creates an empty id for JPA.
     */
    protected A2AConversationId() {
    }

    /**
     * Creates an id.
     *
     * @param conversationId the host conversation id
     * @param agentName the agent name
     * @param skillId the skill id, or {@code null} when no skill was selected
     */
    public A2AConversationId(final String conversationId, final String agentName,
                             final String skillId) {
        this.conversationId = Objects.requireNonNull(conversationId,
                "conversationId must not be null");
        this.agentName = Objects.requireNonNull(agentName, "agentName must not be null");
        this.skillId = skillId == null ? "" : skillId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof A2AConversationId that)) {
            return false;
        }
        return conversationId.equals(that.conversationId)
                && agentName.equals(that.agentName)
                && skillId.equals(that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, agentName, skillId);
    }
}
