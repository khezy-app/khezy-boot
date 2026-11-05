package io.github.khezyapp.a2a.core.client.invoke;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-send client options.
 *
 * @param timeout the overall send timeout
 * @param strategy the wait strategy
 * @param stopOnInterrupt whether to return as soon as the task is interrupted
 * @param headers HTTP headers for this call
 * @param state transport call-context state for this call
 */
public record SendOptions(Duration timeout, Strategy strategy, boolean stopOnInterrupt,
                          Map<String, String> headers, Map<String, Object> state) {

    /**
     * The default send timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Creates options with defensive map copies.
     */
    public SendOptions {
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        state = state == null ? Map.of() : copyState(state);
    }

    /**
     * Returns the default options.
     *
     * @return blocking send with a 60 second timeout
     */
    public static SendOptions defaults() {
        return new SendOptions(DEFAULT_TIMEOUT, Strategy.BLOCKING, true, Map.of(), Map.of());
    }

    /**
     * Returns a builder pre-populated with the defaults.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder pre-populated with this value.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Wait strategy for a send.
     *
     * <p>Only {@link #BLOCKING} is implemented in v2.0. {@link #POLL} and
     * {@link #STREAM} are reserved for later versions and currently fall back to the
     * blocking invoker.</p>
     */
    public enum Strategy {
        /**
         * Wait until the task is settled or interrupted.
         */
        BLOCKING,

        /**
         * Send and return a pending outcome for later polling.
         */
        POLL,

        /**
         * Consume the server-sent event stream.
         */
        STREAM
    }

    /**
     * Builder for {@link SendOptions}.
     */
    public static final class Builder {

        private Duration timeout = DEFAULT_TIMEOUT;
        private Strategy strategy = Strategy.BLOCKING;
        private boolean stopOnInterrupt = true;
        private Map<String, String> headers = Map.of();
        private Map<String, Object> state = Map.of();

        private Builder() {
        }

        private Builder(final SendOptions options) {
            this.timeout = options.timeout();
            this.strategy = options.strategy();
            this.stopOnInterrupt = options.stopOnInterrupt();
            this.headers = options.headers();
            this.state = options.state();
        }

        /**
         * Sets the overall timeout.
         *
         * @param value the timeout
         * @return this builder
         */
        public Builder timeout(final Duration value) {
            this.timeout = value;
            return this;
        }

        /**
         * Sets the wait strategy.
         *
         * @param value the strategy
         * @return this builder
         */
        public Builder strategy(final Strategy value) {
            this.strategy = value;
            return this;
        }

        /**
         * Sets whether to return as soon as the task is interrupted.
         *
         * @param value the flag
         * @return this builder
         */
        public Builder stopOnInterrupt(final boolean value) {
            this.stopOnInterrupt = value;
            return this;
        }

        /**
         * Sets per-call HTTP headers.
         *
         * @param value the headers
         * @return this builder
         */
        public Builder headers(final Map<String, String> value) {
            this.headers = value;
            return this;
        }

        /**
         * Sets per-call transport state.
         *
         * @param value the state map
         * @return this builder
         */
        public Builder state(final Map<String, Object> value) {
            this.state = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public SendOptions build() {
            return new SendOptions(timeout, strategy, stopOnInterrupt, headers, state);
        }
    }

    private static Map<String, Object> copyState(final Map<String, Object> source) {
        final var copy = new LinkedHashMap<String, Object>(source.size());
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
