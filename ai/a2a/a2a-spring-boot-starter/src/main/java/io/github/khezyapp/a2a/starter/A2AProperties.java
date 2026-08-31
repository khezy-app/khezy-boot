package io.github.khezyapp.a2a.starter;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the KHEZY A2A starter. Prefix: {@code io.github.khezyapp.a2a}.
 * These values feed the default {@link org.a2aproject.sdk.spec.AgentCard} bean; provide
 * your own {@code Supplier<AgentCard>} bean to bypass them entirely. The {@code client}
 * section drives the auto-configured outbound {@code A2ARemoteAgentClient}, which only
 * activates when {@code client.base-url} is set.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "io.github.khezyapp.a2a")
public class A2AProperties {

    /** Display name published in the agent card. */
    private String agentCardName = "Khezy A2A Agent";

    /** Human-readable description published in the agent card. */
    private String agentCardDescription = "";

    /** Base URL where this agent is reachable, published in the agent card. */
    private String agentCardUrl = "";

    /** Version of this agent, published in the agent card. */
    private String agentCardVersion = "1.0.0";

    /** Whether streaming (SSE) is advertised in the agent card capabilities. */
    private boolean streamingEnabled = true;

    /** Outbound remote-agent client settings (see {@link Client}). */
    private final Client client = new Client();

    /** Authentication scheme applied to outgoing calls (see {@link Auth}). */
    public enum AuthType {

        /** No auth header is added automatically. */
        NONE,

        /** A bearer token is added to every request via an interceptor. */
        BEARER
    }

    /** Settings for calling a remote agent over JSON-RPC. */
    @Getter
    @Setter
    public static class Client {

        /**
         * Base URL of the remote agent to call; its {@code /.well-known/agent-card.json}
         * is fetched at startup for discovery. The auto-configured client bean exists
         * only when this is set.
         */
        private String baseUrl = "";

        /** Default reply timeout for {@code sendMessage}. */
        private Duration timeout = Duration.ofSeconds(30);

        /** Outbound authentication settings. */
        private final Auth auth = new Auth();
    }

    /** Outbound authentication settings. */
    @Getter
    @Setter
    public static class Auth {

        /** Scheme applied to every outgoing request; {@code NONE} adds nothing. */
        private AuthType type = AuthType.NONE;

        /**
         * Static bearer token used when {@code type=bearer}. For dynamic tokens define
         * your own {@code ClientCallInterceptor} bean instead.
         */
        private String token = "";
    }
}
