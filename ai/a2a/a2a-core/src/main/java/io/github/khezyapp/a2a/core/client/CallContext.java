package io.github.khezyapp.a2a.core.client;

import java.util.Map;
import java.util.Objects;

/**
 * Per-call metadata for outbound remote-agent calls: HTTP headers plus opaque
 * attributes. Headers are merged onto every request the transport sends; attributes
 * travel through client-side interceptors only. Use {@link #NONE} when a call carries
 * nothing extra. Mirrors the server-side {@code dispatch.CallContext} naming.
 */
public record CallContext(Map<String, String> headers, Map<String, Object> attributes) {

    /** Shared immutable instance for calls without extra metadata. */
    public static final CallContext NONE = new CallContext(Map.of(), Map.of());

    public CallContext {
        headers = Objects.requireNonNull(headers, "headers");
        attributes = Objects.requireNonNull(attributes, "attributes");
    }

    /** Convenience factory for header-only contexts (e.g., a per-call Authorization). */
    public static CallContext ofHeaders(final Map<String, String> headers) {
        return new CallContext(Map.copyOf(headers), Map.of());
    }
}
