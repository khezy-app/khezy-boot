package io.github.khezyapp.a2a.starter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the KHEZY A2A client wiring.
 *
 * <p>Prefix: {@code io.github.khezyapp.a2a.client}. When {@code enabled} is {@code false}
 * no client beans are registered.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = A2AClientProperties.PREFIX)
public class A2AClientProperties {

    /** Configuration prefix. */
    public static final String PREFIX = "io.github.khezyapp.a2a.client";

    /** Master switch for the client auto-configuration. */
    private boolean enabled = true;

    /** Agent base URLs registered at startup. */
    private List<String> urls = new ArrayList<>();

    /** Non-standard agent card path; blank uses the SDK well-known location. */
    private String agentCardPath = "";

    /** Headers sent with agent card requests. */
    private Map<String, String> authHeaders = new LinkedHashMap<>();

    /** Default timeout for blocking sends; per-call options win. */
    private Duration callTimeout = Duration.ofSeconds(60);

    /** Fallback card freshness when the response carries no {@code Cache-Control}. */
    private Duration cardCacheTtl = Duration.ofMinutes(5);

    /** Whether streaming is requested when the card supports it. */
    private boolean streaming;

    /** Whether the client transport order wins over the card order. */
    private boolean useClientPreference;

    /** Idle conversation retention. */
    private Duration conversationStateTtl = Duration.ofHours(1);

    /** Maximum number of remembered conversations. */
    private int maxConversations = 1000;

    /** Output modes requested from agents; empty uses the card defaults. */
    private List<String> acceptedOutputModes = new ArrayList<>();

    /** Extra parameters passed to SDK transport wrappers (for example OpenTelemetry). */
    private Map<String, Object> transportParameters = new LinkedHashMap<>();

    /**
     * Whether the discovered skill/agent tools are attached to every auto-configured
     * {@code ChatClient}. Off by default; set it only when the host wants every client
     * to see the remote agents.
     */
    private boolean attachToolsToChatClient;

    /** Server-sent event parser limits. */
    private final Sse sse = new Sse();

    /** SSE safety limits preventing unbounded-line memory exhaustion. */
    @Getter
    @Setter
    public static class Sse {

        /** Maximum bytes per raw SSE line. */
        private int maxLineLength = 1024 * 1024;

        /** Maximum {@code data:} lines per event. */
        private int maxBufferLines = 1000;

        /** Maximum characters per event. */
        private int maxBufferChars = 1024 * 1024;
    }
}
