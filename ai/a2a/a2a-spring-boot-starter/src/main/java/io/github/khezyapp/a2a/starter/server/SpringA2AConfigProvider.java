package io.github.khezyapp.a2a.starter.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.springframework.core.env.Environment;

/**
 * Bridges the SDK configuration lookup onto Spring's {@link Environment}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>an explicit SDK key in the Spring environment (for example
 *       {@code a2a.blocking.agent.timeout.seconds}), which lets power users override
 *       everything;</li>
 *   <li>values mapped from {@code io.github.khezyapp.a2a.server.*} properties;</li>
 *   <li>the SDK's bundled {@code META-INF/a2a-defaults.properties} values.</li>
 * </ol>
 *
 * <p>This provider is required: the SDK request handler reads its timeouts, authorization
 * and push-notification flags through it during {@code @PostConstruct} and overwrites the
 * builder values.</p>
 */
public class SpringA2AConfigProvider implements A2AConfigProvider {

    private final Environment environment;
    private final A2AConfigProvider defaults;
    private final Map<String, String> overrides;

    /**
     * Creates a provider without starter-property overrides.
     *
     * @param environment the Spring environment
     * @param defaults the SDK defaults
     */
    public SpringA2AConfigProvider(final Environment environment, final A2AConfigProvider defaults) {
        this(environment, defaults, Map.of());
    }

    /**
     * Creates a provider.
     *
     * @param environment the Spring environment
     * @param defaults the SDK defaults
     * @param overrides values mapped from the starter properties
     */
    public SpringA2AConfigProvider(final Environment environment, final A2AConfigProvider defaults,
                                   final Map<String, String> overrides) {
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.defaults = Objects.requireNonNull(defaults, "defaults must not be null");
        this.overrides = copy(overrides);
    }

    @Override
    public String getValue(final String name) {
        return getOptionalValue(name).orElseThrow(() ->
                new IllegalArgumentException("No A2A configuration value found for: " + name));
    }

    @Override
    public Optional<String> getOptionalValue(final String name) {
        Objects.requireNonNull(name, "name must not be null");
        final var environmentValue = environment.getProperty(name);
        if (environmentValue != null) {
            return Optional.of(environmentValue);
        }
        final var override = overrides.get(name);
        if (override != null) {
            return Optional.of(override);
        }
        return defaults.getOptionalValue(name);
    }

    private static Map<String, String> copy(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
