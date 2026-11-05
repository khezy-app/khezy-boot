package io.github.khezyapp.a2a.core.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.auth.CredentialService;

/**
 * Inputs for building an SDK client.
 *
 * <p>The starter owns one {@link A2AHttpClient} bean and passes it here so card
 * resolution and every transport share the same connection pool and SSE limits.
 * Transport parameters activate SDK transport wrappers (for example OpenTelemetry)
 * without this module depending on them.</p>
 *
 * @param acceptedOutputModes output modes requested from the agent, empty for card defaults
 * @param streaming whether streaming is requested when the card supports it
 * @param polling whether the client should poll instead of waiting for task updates
 * @param useClientPreference whether client transport order wins over the card order
 * @param interceptors extra client call interceptors
 * @param credentialService optional credential source for SDK authentication
 * @param httpClient the shared HTTP client
 * @param transportParameters optional transport wrapper parameters
 */
public record ClientBuildOptions(List<String> acceptedOutputModes,
                                 boolean streaming,
                                 boolean polling,
                                 boolean useClientPreference,
                                 List<ClientCallInterceptor> interceptors,
                                 CredentialService credentialService,
                                 A2AHttpClient httpClient,
                                 Map<String, Object> transportParameters) {

    /**
     * Creates build options with defensive copies.
     */
    public ClientBuildOptions {
        acceptedOutputModes = copyStrings(acceptedOutputModes);
        interceptors = Objects.isNull(interceptors) ? List.of() : List.copyOf(interceptors);
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        transportParameters = copy(transportParameters);
    }

    /**
     * Creates options with the legacy two-component shape.
     *
     * @param httpClient the shared HTTP client
     * @param transportParameters optional transport wrapper parameters
     */
    public ClientBuildOptions(final A2AHttpClient httpClient,
                              final Map<String, Object> transportParameters) {
        this(List.of(), true, false, false, List.of(), null, httpClient, transportParameters);
    }

    /**
     * Creates options without transport parameters.
     *
     * @param httpClient the shared HTTP client
     * @return the options
     */
    public static ClientBuildOptions of(final A2AHttpClient httpClient) {
        return new ClientBuildOptions(httpClient, Map.of());
    }

    /**
     * Returns a builder pre-populated with the defaults.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private static List<String> copyStrings(final List<String> source) {
        if (Objects.isNull(source) || source.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(source));
    }

    private static Map<String, Object> copy(final Map<String, Object> source) {
        if (Objects.isNull(source) || source.isEmpty()) {
            return Map.of();
        }
        final var copy = new LinkedHashMap<String, Object>(source.size());
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Builder for {@link ClientBuildOptions}.
     */
    public static final class Builder {

        private List<String> acceptedOutputModes = List.of();
        private boolean streaming = true;
        private boolean polling;
        private boolean useClientPreference;
        private List<ClientCallInterceptor> interceptors = List.of();
        private CredentialService credentialService;
        private A2AHttpClient httpClient;
        private Map<String, Object> transportParameters = Map.of();

        private Builder() {
        }

        /**
         * Sets the accepted output modes.
         *
         * @param value the output modes
         * @return this builder
         */
        public Builder acceptedOutputModes(final List<String> value) {
            this.acceptedOutputModes = value;
            return this;
        }

        /**
         * Sets whether streaming is requested.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder streaming(final boolean value) {
            this.streaming = value;
            return this;
        }

        /**
         * Sets whether polling is requested.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder polling(final boolean value) {
            this.polling = value;
            return this;
        }

        /**
         * Sets whether client transport preference wins.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder useClientPreference(final boolean value) {
            this.useClientPreference = value;
            return this;
        }

        /**
         * Sets extra client call interceptors.
         *
         * @param value the interceptors
         * @return this builder
         */
        public Builder interceptors(final List<ClientCallInterceptor> value) {
            this.interceptors = value;
            return this;
        }

        /**
         * Sets the optional credential service.
         *
         * @param value the credential service
         * @return this builder
         */
        public Builder credentialService(final CredentialService value) {
            this.credentialService = value;
            return this;
        }

        /**
         * Sets the shared HTTP client.
         *
         * @param value the HTTP client
         * @return this builder
         */
        public Builder httpClient(final A2AHttpClient value) {
            this.httpClient = value;
            return this;
        }

        /**
         * Sets transport wrapper parameters.
         *
         * @param value the parameters
         * @return this builder
         */
        public Builder transportParameters(final Map<String, Object> value) {
            this.transportParameters = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public ClientBuildOptions build() {
            return new ClientBuildOptions(
                    acceptedOutputModes,
                    streaming,
                    polling,
                    useClientPreference,
                    interceptors,
                    credentialService,
                    httpClient,
                    transportParameters);
        }
    }
}
