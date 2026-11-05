package io.github.khezyapp.a2a.starter.springai;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.github.khezyapp.a2a.springai.A2AAgentTools;
import io.github.khezyapp.a2a.springai.A2APromptProvider;
import io.github.khezyapp.a2a.springai.A2AResultFormatter;
import io.github.khezyapp.a2a.springai.A2ASkillToolFactory;
import io.github.khezyapp.a2a.starter.A2AClientProperties;

import static org.assertj.core.api.Assertions.assertThat;

class A2AToolingAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    @DisplayName("Tooling is an auto-configuration")
    void shouldBeAutoConfiguration() {
        assertThat(A2AToolingAutoConfiguration.class.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    @DisplayName("Tooling requires Spring AI and the optional a2a-spring-ai module")
    void shouldBeConditionalOnSpringAi() {
        final var conditional = A2AToolingAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).contains(ChatClient.class, A2ASkillToolFactory.class);
    }

    @Test
    @DisplayName("Tooling follows the A2A client switch")
    void shouldFollowClientSwitch() {
        final var conditional = A2AToolingAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.prefix()).isEqualTo(A2AClientProperties.PREFIX);
        assertThat(conditional.name()).containsExactly("enabled");
        assertThat(conditional.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("Every tooling bean backs off when the consumer defines one")
    void shouldConditionEveryBean() {
        assertConditionalBeanMethods(A2AToolingAutoConfiguration.class);
    }

    @Test
    @DisplayName("The tool beans are exposed")
    void shouldExposeToolBeans() {
        assertThat(beanTypes()).contains(
                A2AResultFormatter.class,
                A2ASkillToolFactory.class,
                A2AAgentTools.class,
                A2APromptProvider.class,
                ChatClientBuilderCustomizer.class);
    }

    @Test
    @DisplayName("Attaching tools to every ChatClient is opt-in")
    void shouldAttachToolsOnlyOnRequest() {
        final var method = beanMethod(ChatClientBuilderCustomizer.class);
        final var conditional = method.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.name()).containsExactly("attach-tools-to-chat-client");
        assertThat(conditional.havingValue()).isEqualTo("true");
        assertThat(conditional.matchIfMissing()).isFalse();
    }

    @Test
    @DisplayName("Auto-configuration imports contain the tooling configuration")
    void shouldRegisterAutoConfiguration() throws IOException {
        try (InputStream input = A2AToolingAutoConfigurationTest.class.getClassLoader()
                .getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(input).as("classpath resource %s", IMPORTS_RESOURCE).isNotNull();
            final var imports = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(imports).contains(A2AToolingAutoConfiguration.class.getName());
        }
    }

    private static List<Class<?>> beanTypes() {
        return Arrays.stream(A2AToolingAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .map(Method::getReturnType)
                .toList();
    }

    private static Method beanMethod(final Class<?> returnType) {
        return Arrays.stream(A2AToolingAutoConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> method.getReturnType().equals(returnType))
                .findFirst()
                .orElseThrow();
    }

    private static void assertConditionalBeanMethods(final Class<?> type) {
        for (final Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                assertThat(method.getAnnotation(ConditionalOnMissingBean.class))
                        .as("bean method %s.%s", type.getSimpleName(), method.getName())
                        .isNotNull();
            }
        }
    }
}
