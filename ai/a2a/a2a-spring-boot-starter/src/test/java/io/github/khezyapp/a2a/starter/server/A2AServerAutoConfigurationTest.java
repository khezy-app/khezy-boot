package io.github.khezyapp.a2a.starter.server;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.a2aproject.sdk.server.TransportMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.github.khezyapp.a2a.starter.A2AServerProperties;
import io.github.khezyapp.a2a.starter.client.A2AClientAutoConfiguration;
import io.github.khezyapp.a2a.starter.web.A2AWebAutoConfiguration;
import io.github.khezyapp.a2a.starter.web.JsonRpcTransportMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class A2AServerAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    @DisplayName("Server and web configurations are auto-configurations")
    void shouldBeAutoConfigurations() {
        assertThat(A2AServerAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
        assertThat(A2AWebAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
        assertThat(A2AClientAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    @DisplayName("Every auto-configured bean is conditional on a missing bean")
    void shouldConditionEveryBean() {
        assertConditionalBeanMethods(A2AServerAutoConfiguration.class);
        assertConditionalBeanMethods(A2AWebAutoConfiguration.class);
        assertConditionalBeanMethods(A2AClientAutoConfiguration.class);
    }

    @Test
    @DisplayName("Client configuration runs after the JPA store configuration")
    void shouldOrderClientAfterJpaStore() {
        final var annotation = A2AClientAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation.afterName())
                .contains("io.github.khezyapp.a2a.store.jpa.A2AJpaStoreAutoConfiguration");
    }

    @Test
    @DisplayName("Auto-configuration imports contain the server, web and client configurations")
    void shouldRegisterAutoConfigurations() throws IOException {
        final var imports = readClasspathResource(IMPORTS_RESOURCE);

        assertThat(imports).contains(A2AServerAutoConfiguration.class.getName());
        assertThat(imports).contains(A2AWebAutoConfiguration.class.getName());
        assertThat(imports).contains(A2AClientAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("JSON-RPC transport metadata is discoverable through ServiceLoader")
    void shouldRegisterTransportMetadata() {
        final var transports = new ArrayList<String>();
        ServiceLoader.load(TransportMetadata.class)
                .forEach(metadata -> transports.add(metadata.getTransportProtocol()));

        assertThat(transports).contains("JSONRPC");
    }

    @Test
    @DisplayName("Base path is normalized")
    void shouldNormalizePath() {
        final var properties = new A2AServerProperties();
        assertThat(properties.normalizedPath()).isEqualTo("/a2a");

        properties.setPath("a2a/");
        assertThat(properties.normalizedPath()).isEqualTo("/a2a");

        properties.setPath("/");
        assertThat(properties.normalizedPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("SDK overrides use the exact SDK property names")
    void shouldMapSdkOverrides() {
        final var properties = new A2AServerProperties();
        properties.getPushNotifications().setEnabled(true);
        properties.setPopulateReferredTasks(true);

        final var overrides = A2AServerAutoConfiguration.sdkOverrides(properties);

        assertThat(overrides).containsEntry(A2AServerAutoConfiguration.AGENT_TIMEOUT_KEY, "600");
        assertThat(overrides).containsEntry(A2AServerAutoConfiguration.CONSUMPTION_TIMEOUT_KEY, "60");
        assertThat(overrides).containsEntry(A2AServerAutoConfiguration.RECONCILIATION_TIMEOUT_KEY, "5");
        assertThat(overrides).containsEntry(A2AServerAutoConfiguration.POPULATE_REFERRED_TASKS_KEY, "true");
        assertThat(overrides).containsEntry(A2AServerAutoConfiguration.PUSH_NOTIFICATIONS_ENABLED_KEY, "true");
        assertThat(overrides).containsKey(A2AServerAutoConfiguration.AUTHORIZATION_REQUIRED_KEY);
    }

    @Test
    @DisplayName("Explicit authorization setting wins over classpath detection")
    void shouldHonourExplicitAuthorization() {
        final var properties = new A2AServerProperties();
        properties.getAuthorization().setRequired("false");
        assertThat(A2AServerAutoConfiguration.authorizationRequired(properties)).isFalse();

        properties.getAuthorization().setRequired("true");
        assertThat(A2AServerAutoConfiguration.authorizationRequired(properties)).isTrue();

        properties.getAuthorization().setRequired("nonsense");
        assertThatThrownBy(() -> A2AServerAutoConfiguration.authorizationRequired(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authorization.required");
    }

    @Test
    @DisplayName("The transport metadata class implements the SDK contract")
    void shouldImplementTransportMetadata() {
        assertThat(new JsonRpcTransportMetadata()).isInstanceOf(TransportMetadata.class);
        assertThat(new JsonRpcTransportMetadata().getTransportProtocol()).isEqualTo("JSONRPC");
    }

    private static void assertConditionalBeanMethods(final Class<?> type) {
        for (final Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                assertThat(method.getAnnotation(ConditionalOnMissingBean.class))
                        .as("bean method %s.%s", type.getSimpleName(), method.getName())
                        .isNotNull();
            }
        }
        for (final Class<?> nested : type.getDeclaredClasses()) {
            assertConditionalBeanMethods(nested);
        }
    }

    private static List<String> readClasspathResource(final String name) throws IOException {
        try (InputStream input = A2AServerAutoConfigurationTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as("classpath resource %s", name).isNotNull();
            return List.of(new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        }
    }
}
