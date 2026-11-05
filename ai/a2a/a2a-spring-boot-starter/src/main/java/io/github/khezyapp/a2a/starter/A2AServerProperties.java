package io.github.khezyapp.a2a.starter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the KHEZY A2A server runtime.
 *
 * <p>Prefix: {@code io.github.khezyapp.a2a.server}. Everything the starter assembles around
 * the SDK server graph is configurable here; a consumer-provided {@code AgentCard} or
 * {@code AgentExecutor} bean always wins over these values.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = A2AServerProperties.PREFIX)
public class A2AServerProperties {

    /** Configuration prefix. */
    public static final String PREFIX = "io.github.khezyapp.a2a.server";

    /** Master switch for the server auto-configuration. */
    private boolean enabled = true;

    /** Base path for the JSON-RPC endpoint and the agent card. */
    private String path = "/a2a";

    /** Agent card published at the well-known location when no card bean exists. */
    private final AgentCard agentCard = new AgentCard();

    /** Authorization settings. */
    private final Authorization authorization = new Authorization();

    /** Blocking request timeouts. */
    private final Blocking blocking = new Blocking();

    /** Push notification settings. */
    private final PushNotifications pushNotifications = new PushNotifications();

    /** Whether {@code referenceTaskIds} are resolved into related tasks. */
    private boolean populateReferredTasks;

    /** Multi-tenancy settings. */
    private final Multitenancy multitenancy = new Multitenancy();

    /** Executor thread settings. */
    private final Executor executor = new Executor();

    /** Agent card cache settings. */
    private final CardCache cardCache = new CardCache();

    /** Call-context header settings. */
    private final CallContext callContext = new CallContext();

    /**
     * Returns the base path normalized to start with {@code /} and without a trailing
     * {@code /}, so controllers and the card URL can be composed without double slashes.
     *
     * @return the normalized base path
     */
    public String normalizedPath() {
        var value = Objects.isNull(path) || path.isBlank() ? "/a2a" : path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** Agent card values used to build the default card bean. */
    @Getter
    @Setter
    public static class AgentCard {

        /** Display name published in the card. */
        private String name = "Khezy A2A Agent";

        /** Human-readable description published in the card. */
        private String description = "A KHEZY A2A agent";

        /** Agent version published in the card. */
        private String version = "1.0.0";

        /**
         * Public URL of the JSON-RPC endpoint (including the configured base path).
         * Defaults to {@code http://localhost:8080} plus the base path; set it explicitly
         * in production.
         */
        private String url = "";

        /** Optional documentation URL. */
        private String documentationUrl = "";

        /** Optional icon URL. */
        private String iconUrl = "";

        /** Provider information. */
        private final Provider provider = new Provider();

        /** Card capabilities. */
        private final Capabilities capabilities = new Capabilities();

        /** Default input modes. */
        private List<String> defaultInputModes = new ArrayList<>(List.of("text"));

        /** Default output modes. */
        private List<String> defaultOutputModes = new ArrayList<>(List.of("text"));

        /** Skills exposed by the agent. */
        private List<Skill> skills = new ArrayList<>();
    }

    /** Agent provider information. */
    @Getter
    @Setter
    public static class Provider {

        /** Organization name. */
        private String organization = "";

        /** Organization URL. */
        private String url = "";
    }

    /** Agent card capabilities. */
    @Getter
    @Setter
    public static class Capabilities {

        /** Whether streaming responses are advertised. */
        private boolean streaming = true;

        /** Whether push notifications are advertised. */
        private boolean pushNotifications;

        /** Whether an extended agent card is advertised. */
        private boolean extendedAgentCard;
    }

    /** Agent card skill. */
    @Getter
    @Setter
    public static class Skill {

        /** Skill identifier. */
        private String id;

        /** Skill name. */
        private String name;

        /** Skill description. */
        private String description = "";

        /** Skill tags. */
        private List<String> tags = new ArrayList<>();

        /** Example prompts. */
        private List<String> examples = new ArrayList<>();

        /** Supported input modes. */
        private List<String> inputModes = new ArrayList<>();

        /** Supported output modes. */
        private List<String> outputModes = new ArrayList<>();
    }

    /** Authorization settings. */
    @Getter
    @Setter
    public static class Authorization {

        /**
         * Whether SDK task authorization is enforced: {@code auto}, {@code true} or
         * {@code false}. {@code auto} enables it when Spring Security is on the classpath
         * and disables it with a startup warning otherwise (the SDK is fail-closed when no
         * provider is configured).
         */
        private String required = "auto";
    }

    /** Blocking request timeouts. */
    @Getter
    @Setter
    public static class Blocking {

        /** How long a blocking send waits for the agent. */
        private Duration agentTimeout = Duration.ofSeconds(600);

        /** How long events are consumed before a blocking send returns. */
        private Duration consumptionTimeout = Duration.ofSeconds(60);

        /** How long the task store is polled for reconciliation. */
        private Duration reconciliationTimeout = Duration.ofSeconds(5);
    }

    /** Push notification settings. */
    @Getter
    @Setter
    public static class PushNotifications {

        /** Whether SDK push notifications are enabled. */
        private boolean enabled;
    }

    /** Multi-tenancy settings. */
    @Getter
    @Setter
    public static class Multitenancy {

        /** Whether default tenant routers and tenant card paths are enabled. */
        private boolean enabled;
    }

    /** Executor thread settings. */
    @Getter
    @Setter
    public static class Executor {

        /** Whether agent execution uses virtual threads. */
        private boolean virtualThreads = true;

        /** Platform pool size used when virtual threads are disabled. */
        private int platformThreads = 32;

        /** Platform pool queue capacity used when virtual threads are disabled. */
        private int platformQueueCapacity = 1000;
    }

    /** Agent card cache settings. */
    @Getter
    @Setter
    public static class CardCache {

        /** {@code Cache-Control: max-age} served with the agent card. */
        private Duration maxAge = Duration.ofSeconds(60);
    }

    /** Call-context header settings. */
    @Getter
    @Setter
    public static class CallContext {

        /**
         * Header carrying the SDK tenant used for routing. Blank disables tenant
         * resolution (single-tenant server).
         */
        private String tenantHeader = "";

        /**
         * Header carrying the application-owned agent scope used for task isolation.
         * Blank disables scope resolution.
         */
        private String scopeHeader = "";
    }
}
