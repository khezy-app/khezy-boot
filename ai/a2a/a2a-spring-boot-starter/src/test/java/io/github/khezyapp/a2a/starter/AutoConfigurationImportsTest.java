package io.github.khezyapp.a2a.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationImportsTest {

    private static final String IMPORTS_LOCATION =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String A2A_AUTO_CONFIGURATION =
            "io.github.khezyapp.a2a.starter.A2AAutoConfiguration";

    private static final String A2A_WEB_AUTO_CONFIGURATION =
            "io.github.khezyapp.a2a.starter.web.A2AWebAutoConfiguration";

    @Test
    @DisplayName("AutoConfiguration.imports registers the A2A auto-configuration")
    void shouldRegisterA2AAutoConfiguration() throws IOException {
        assertThat(imports()).contains(A2A_AUTO_CONFIGURATION);
    }

    @Test
    @DisplayName("AutoConfiguration.imports registers the web endpoints auto-configuration")
    void shouldRegisterWebAutoConfiguration() throws IOException {
        assertThat(imports()).contains(A2A_WEB_AUTO_CONFIGURATION);
    }

    private static List<String> imports() throws IOException {
        try (var stream = AutoConfigurationImportsTest.class.getClassLoader()
                .getResourceAsStream(IMPORTS_LOCATION)) {
            assertThat(stream).as(IMPORTS_LOCATION + " on test classpath").isNotNull();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            }
        }
    }
}

