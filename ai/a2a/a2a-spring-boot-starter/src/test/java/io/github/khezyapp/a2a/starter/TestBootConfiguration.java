package io.github.khezyapp.a2a.starter;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Anchor for slice tests ({@code @WebMvcTest}): Boot requires a discoverable
 * {@code @SpringBootConfiguration} even though this library ships no application class.
 * It deliberately carries no {@code @ComponentScan} and no beans — the web-layer tests
 * import exactly the controller under test, so no full application context is loaded.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestBootConfiguration {
}
