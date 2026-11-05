package io.github.khezyapp.a2a.springai;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.khezyapp.a2a.core.client.RemoteSkill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2APromptBuilderTest {

    private static final RemoteSkill ADD = TestAgents.skill("math", "add", "Add", "Adds numbers");

    @Test
    @DisplayName("The default prompt routes to the most specific tool")
    void shouldBuildDefaultPrompt() {
        final var prompt = new A2APromptBuilder().build(List.of(ADD));

        assertTrue(prompt.contains("most specific tool"));
        assertTrue(prompt.contains("listRemoteAgents"));
        assertTrue(prompt.contains("restate"));
        assertTrue(prompt.contains(A2APromptBuilder.DEFAULT_SKILLS_HEADER));
        assertTrue(prompt.contains("- math / add: Add - Adds numbers"));
    }

    @Test
    @DisplayName("Custom instructions replace the default instructions")
    void shouldUseCustomInstructions() {
        final var prompt = A2APromptBuilder.builder()
                .instructions("Route finance requests only.")
                .build()
                .build(List.of(ADD));

        assertTrue(prompt.startsWith("Route finance requests only."));
        assertFalse(prompt.contains("You are a routing assistant"));
    }

    @Test
    @DisplayName("A custom skill template is rendered per skill")
    void shouldUseCustomSkillTemplate() {
        final var prompt = A2APromptBuilder.builder()
                .skillsHeader("Tools:")
                .skillTemplate("{agent}::{id} ({tags})")
                .build()
                .build(List.of(ADD));

        assertTrue(prompt.contains("Tools:"));
        assertTrue(prompt.contains("math::add (math)"));
    }

    @Test
    @DisplayName("No skills produce the empty prompt")
    void shouldBuildEmptyPrompt() {
        final var builder = new A2APromptBuilder();

        assertEquals(A2APromptBuilder.DEFAULT_EMPTY_PROMPT, builder.build(List.of()));
        assertEquals(A2APromptBuilder.DEFAULT_EMPTY_PROMPT, builder.build(null));
    }

    @Test
    @DisplayName("A custom empty prompt is used when nothing is discovered")
    void shouldUseCustomEmptyPrompt() {
        final var prompt = A2APromptBuilder.builder()
                .emptyPrompt("No tools today.")
                .build()
                .build(List.of());

        assertEquals("No tools today.", prompt);
    }

    @Test
    @DisplayName("Card text containing braces is never re-interpreted")
    void shouldNotReinterpretPlaceholders() {
        final var tricky = TestAgents.skill("math", "add", "Add", "Adds numbers like {agent}");
        final var prompt = A2APromptBuilder.builder()
                .instructions("")
                .skillsHeader("")
                .skillTemplate("{name}{description}")
                .build()
                .build(List.of(tricky));

        assertEquals("Add - Adds numbers like {agent}\n", prompt);
    }

    @Test
    @DisplayName("Unknown placeholders are kept verbatim")
    void shouldKeepUnknownPlaceholders() {
        final var prompt = A2APromptBuilder.builder()
                .instructions("")
                .skillsHeader("")
                .skillTemplate("{name} {unknown}")
                .build()
                .build(List.of(ADD));

        assertEquals("Add {unknown}\n", prompt);
    }
}
