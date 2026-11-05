package io.github.khezyapp.a2a.springai;

import java.util.List;

import io.github.khezyapp.a2a.core.client.RemoteSkill;

/**
 * Renders the routing system prompt from the discovered skills.
 *
 * <p>Implement this bean to fully replace the default prompt (for example to tune routing
 * for a business domain). {@link A2APromptBuilder} is the default implementation.</p>
 */
@FunctionalInterface
public interface A2APromptProvider {

    /**
     * Builds the routing prompt.
     *
     * @param skills the discovered skills, possibly empty
     * @return the system prompt
     */
    String build(List<RemoteSkill> skills);
}
