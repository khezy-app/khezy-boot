package io.github.khezyapp.a2a.starter.server;

import java.util.Map;
import java.util.Optional;

import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringA2AConfigProviderTest {

    private static final String AGENT_TIMEOUT = "a2a.blocking.agent.timeout.seconds";

    @Test
    @DisplayName("Environment values win over overrides and SDK defaults")
    void shouldPreferEnvironment() {
        final var environment = new MockEnvironment().withProperty(AGENT_TIMEOUT, "11");
        final var provider = new SpringA2AConfigProvider(environment,
                defaults(Map.of(AGENT_TIMEOUT, "5")), Map.of(AGENT_TIMEOUT, "600"));

        assertThat(provider.getValue(AGENT_TIMEOUT)).isEqualTo("11");
        assertThat(provider.getOptionalValue(AGENT_TIMEOUT)).contains("11");
    }

    @Test
    @DisplayName("Starter overrides win over SDK defaults")
    void shouldPreferOverrides() {
        final var provider = new SpringA2AConfigProvider(new MockEnvironment(),
                defaults(Map.of(AGENT_TIMEOUT, "5")), Map.of(AGENT_TIMEOUT, "600"));

        assertThat(provider.getValue(AGENT_TIMEOUT)).isEqualTo("600");
    }

    @Test
    @DisplayName("SDK defaults are the last fallback")
    void shouldFallBackToDefaults() {
        final var provider = new SpringA2AConfigProvider(new MockEnvironment(),
                defaults(Map.of(AGENT_TIMEOUT, "5")), Map.of());

        assertThat(provider.getValue(AGENT_TIMEOUT)).isEqualTo("5");
        assertThat(provider.getOptionalValue(AGENT_TIMEOUT)).contains("5");
    }

    @Test
    @DisplayName("Unknown required keys fail fast")
    void shouldFailForUnknownKey() {
        final var provider = new SpringA2AConfigProvider(new MockEnvironment(), defaults(Map.of()), Map.of());

        assertThatThrownBy(() -> provider.getValue("a2a.unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a2a.unknown");
        assertThat(provider.getOptionalValue("a2a.unknown")).isEmpty();
    }

    private static A2AConfigProvider defaults(final Map<String, String> values) {
        return new A2AConfigProvider() {
            @Override
            public String getValue(final String name) {
                return Optional.ofNullable(values.get(name))
                        .orElseThrow(() -> new IllegalArgumentException("No default for " + name));
            }

            @Override
            public Optional<String> getOptionalValue(final String name) {
                return Optional.ofNullable(values.get(name));
            }
        };
    }
}
