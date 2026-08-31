package io.github.khezyapp.a2a.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2APropertiesTest {

    @Test
    @DisplayName("New instance carries documented defaults")
    void shouldHaveDocumentedDefaults() {
        final var properties = new A2AProperties();
        assertThat(properties.getAgentCardName()).isEqualTo("Khezy A2A Agent");
        assertThat(properties.getAgentCardDescription()).isEmpty();
        assertThat(properties.getAgentCardUrl()).isEmpty();
        assertThat(properties.getAgentCardVersion()).isEqualTo("1.0.0");
        assertThat(properties.isStreamingEnabled()).isTrue();
    }

    @Test
    @DisplayName("Setters allow overriding every field")
    void shouldAllowOverridingAllFields() {
        final var properties = new A2AProperties();
        properties.setAgentCardName("name");
        properties.setAgentCardDescription("description");
        properties.setAgentCardUrl("url");
        properties.setAgentCardVersion("version");
        properties.setStreamingEnabled(false);
        assertThat(properties.getAgentCardName()).isEqualTo("name");
        assertThat(properties.getAgentCardDescription()).isEqualTo("description");
        assertThat(properties.getAgentCardUrl()).isEqualTo("url");
        assertThat(properties.getAgentCardVersion()).isEqualTo("version");
        assertThat(properties.isStreamingEnabled()).isFalse();
    }
}
