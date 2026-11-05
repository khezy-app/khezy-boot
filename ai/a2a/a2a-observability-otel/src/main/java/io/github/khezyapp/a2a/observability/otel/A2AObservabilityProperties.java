package io.github.khezyapp.a2a.observability.otel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional OpenTelemetry integration.
 *
 * <p>Request and response capture is disabled by default because the SDK decorator
 * serializes message payloads into span attributes, which may contain personal data.
 * Enabling either flag sets the SDK system properties for the lifetime of the JVM; the
 * flags are global by design, matching the SDK decorator contract.</p>
 */
@ConfigurationProperties(prefix = A2AObservabilityProperties.PREFIX)
public class A2AObservabilityProperties {

    /** Property prefix for the OpenTelemetry integration. */
    public static final String PREFIX = "io.github.khezyapp.a2a.observability.otel";

    private boolean enabled = true;

    private boolean captureRequest;

    private boolean captureResponse;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCaptureRequest() {
        return captureRequest;
    }

    public void setCaptureRequest(final boolean captureRequest) {
        this.captureRequest = captureRequest;
    }

    public boolean isCaptureResponse() {
        return captureResponse;
    }

    public void setCaptureResponse(final boolean captureResponse) {
        this.captureResponse = captureResponse;
    }
}
