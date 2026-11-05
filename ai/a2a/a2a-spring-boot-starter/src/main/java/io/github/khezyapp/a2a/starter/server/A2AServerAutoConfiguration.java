package io.github.khezyapp.a2a.starter.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.config.DefaultValuesConfigProvider;
import org.a2aproject.sdk.server.events.DefaultTaskStreamLifecycleHook;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.events.TaskStreamLifecycleHook;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.server.multitenancy.AgentExecutorRouter;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.DefaultPushNotificationUrlValidator;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import io.github.khezyapp.a2a.core.server.A2AStoreProvider;
import io.github.khezyapp.a2a.core.server.A2AServerCustomizer;
import io.github.khezyapp.a2a.core.server.TaskScopeLocator;
import io.github.khezyapp.a2a.core.server.TenantTaskAuthorizationProvider;
import io.github.khezyapp.a2a.starter.A2AServerProperties;

/**
 * Assembles the SDK server graph as overridable Spring beans.
 *
 * <p>One {@link AgentCard} bean (or the configured card) plus one {@link AgentExecutor}
 * bean is enough to boot a working JSON-RPC agent server. Every bean is
 * {@link ConditionalOnMissingBean}, so consumers can replace any node of the graph.</p>
 *
 * <p>Store beans from {@code a2a-store-jpa} are ordered before this configuration by name;
 * when none are present an in-memory provider is used.</p>
 */
@AutoConfiguration(afterName = "io.github.khezyapp.a2a.store.jpa.A2AJpaStoreAutoConfiguration")
@ConditionalOnClass({AgentExecutor.class, DefaultRequestHandler.class, JSONRPCHandler.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = A2AServerProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(A2AServerProperties.class)
public class A2AServerAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(A2AServerAutoConfiguration.class);

    /** SDK key for the blocking agent timeout. */
    public static final String AGENT_TIMEOUT_KEY = "a2a.blocking.agent.timeout.seconds";

    /** SDK key for the blocking consumption timeout. */
    public static final String CONSUMPTION_TIMEOUT_KEY = "a2a.blocking.consumption.timeout.seconds";

    /** SDK key for the blocking reconciliation timeout. */
    public static final String RECONCILIATION_TIMEOUT_KEY = "a2a.blocking.reconciliation.timeout.seconds";

    /** SDK key for resolving referred tasks. */
    public static final String POPULATE_REFERRED_TASKS_KEY = "a2a.request-context.populate-referred-tasks";

    /** SDK key for authorization enforcement. */
    public static final String AUTHORIZATION_REQUIRED_KEY = "a2a.authorization.required";

    /** SDK key for push notifications. */
    public static final String PUSH_NOTIFICATIONS_ENABLED_KEY = "a2a.push-notification.enabled";

    private static final String SECURITY_CONTEXT_CLASS =
            "org.springframework.security.core.context.SecurityContextHolder";

    /**
     * Creates the agent card bean when the consumer does not provide one.
     *
     * @param properties the server properties
     * @param environment the environment used to derive the default URL
     * @return the agent card
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentCard agentCard(final A2AServerProperties properties, final Environment environment) {
        final var card = properties.getAgentCard();
        final var url = resolveAgentUrl(card, properties.normalizedPath(), environment);
        final var builder = AgentCard.builder()
                .name(card.getName())
                .description(card.getDescription())
                .version(card.getVersion())
                .capabilities(AgentCapabilities.builder()
                        .streaming(card.getCapabilities().isStreaming())
                        .pushNotifications(card.getCapabilities().isPushNotifications())
                        .extendedAgentCard(card.getCapabilities().isExtendedAgentCard())
                        .build())
                .defaultInputModes(List.copyOf(card.getDefaultInputModes()))
                .defaultOutputModes(List.copyOf(card.getDefaultOutputModes()))
                .skills(card.getSkills().stream().map(A2AServerAutoConfiguration::toSkill).toList())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), url)))
                .url(url)
                .preferredTransport(TransportProtocol.JSONRPC.asString());
        if (!isBlank(card.getProvider().getOrganization())) {
            builder.provider(new AgentProvider(card.getProvider().getOrganization(), card.getProvider().getUrl()));
        }
        if (!isBlank(card.getDocumentationUrl())) {
            builder.documentationUrl(card.getDocumentationUrl());
        }
        if (!isBlank(card.getIconUrl())) {
            builder.iconUrl(card.getIconUrl());
        }
        return builder.build();
    }

    /**
     * Creates the store provider from existing SDK stores, falling back to memory.
     *
     * @param taskStores the task store beans
     * @param stateProviders the task state provider beans
     * @param pushStores the push notification config store beans
     * @return the store provider
     */
    @Bean
    @ConditionalOnMissingBean
    public A2AStoreProvider a2aStoreProvider(final ObjectProvider<TaskStore> taskStores,
                                             final ObjectProvider<TaskStateProvider> stateProviders,
                                             final ObjectProvider<PushNotificationConfigStore> pushStores) {
        final var taskStore = single(taskStores, "TaskStore");
        if (taskStore == null) {
            return A2AStoreProvider.inMemory();
        }
        var stateProvider = single(stateProviders, "TaskStateProvider");
        if (stateProvider == null) {
            if (!(taskStore instanceof TaskStateProvider provider)) {
                throw new IllegalStateException("A TaskStore bean is present but no TaskStateProvider bean "
                        + "exists and the task store does not implement TaskStateProvider.");
            }
            stateProvider = provider;
        }
        final var pushStore = single(pushStores, "PushNotificationConfigStore");
        return A2AStoreProvider.of(taskStore, stateProvider,
                pushStore == null ? new InMemoryPushNotificationConfigStore() : pushStore);
    }

    /**
     * Creates the main event bus.
     *
     * @return the event bus
     */
    @Bean
    @ConditionalOnMissingBean
    public MainEventBus mainEventBus() {
        return new MainEventBus();
    }

    /**
     * Creates the default stream lifecycle hook when the consumer does not provide one.
     *
     * @return the stream lifecycle hook
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskStreamLifecycleHook taskStreamLifecycleHook() {
        return new DefaultTaskStreamLifecycleHook();
    }

    /**
     * Creates the in-memory queue manager.
     *
     * @param storeProvider the store provider
     * @param mainEventBus the event bus
     * @param lifecycleHook the stream lifecycle hook
     * @return the queue manager
     */
    @Bean
    @ConditionalOnMissingBean
    public QueueManager queueManager(final A2AStoreProvider storeProvider, final MainEventBus mainEventBus,
                                     final TaskStreamLifecycleHook lifecycleHook) {
        return new InMemoryQueueManager(storeProvider.taskStateProvider(), mainEventBus, lifecycleHook);
    }

    /**
     * Creates the push notification sender.
     *
     * <p>When push notifications are disabled the sender is a no-op so the SDK graph stays
     * complete without outbound HTTP.</p>
     *
     * @param storeProvider the store provider
     * @param properties the server properties
     * @return the push notification sender
     */
    @Bean
    @ConditionalOnMissingBean
    public PushNotificationSender pushNotificationSender(final A2AStoreProvider storeProvider,
                                                         final A2AServerProperties properties) {
        if (!properties.getPushNotifications().isEnabled()) {
            return (event, taskSnapshot) -> {
                // Push notifications are disabled.
            };
        }
        return new BasePushNotificationSender(storeProvider.pushNotificationConfigStore(),
                new DefaultPushNotificationUrlValidator());
    }

    /**
     * Creates the main event bus processor.
     *
     * <p>Exposed as a bean so Spring runs its {@code @PostConstruct start()} and
     * {@code @PreDestroy stop()} callbacks.</p>
     *
     * @param mainEventBus the event bus
     * @param storeProvider the store provider
     * @param pushSender the push notification sender
     * @param queueManager the queue manager
     * @return the event bus processor
     */
    @Bean
    @ConditionalOnMissingBean
    public MainEventBusProcessor mainEventBusProcessor(final MainEventBus mainEventBus,
                                                       final A2AStoreProvider storeProvider,
                                                       final PushNotificationSender pushSender,
                                                       final QueueManager queueManager) {
        return new MainEventBusProcessor(mainEventBus, storeProvider.taskStore(), pushSender, queueManager);
    }

    /**
     * Creates the executor used to run agent logic.
     *
     * @param properties the server properties
     * @return the executor
     */
    @Bean(name = "a2aAgentExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "a2aAgentExecutor")
    public Executor a2aAgentExecutor(final A2AServerProperties properties) {
        final var executor = properties.getExecutor();
        return A2AExecutors.agentExecutor(executor.isVirtualThreads(), executor.getPlatformThreads(),
                executor.getPlatformQueueCapacity());
    }

    /**
     * Creates the executor used to consume agent events.
     *
     * @return the executor
     */
    @Bean(name = "a2aEventConsumerExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "a2aEventConsumerExecutor")
    public Executor a2aEventConsumerExecutor() {
        return A2AExecutors.eventConsumerExecutor();
    }

    /**
     * Creates the SDK default-values provider.
     *
     * <p>Registered as a non-autowire candidate: it exists so the Spring-backed provider
     * can read the SDK defaults, but it must never be injected into the SDK handler,
     * whose CDI-style config-provider field Spring would otherwise see as ambiguous.</p>
     *
     * @return the SDK defaults
     */
    @Bean(autowireCandidate = false)
    @ConditionalOnMissingBean
    public DefaultValuesConfigProvider a2aDefaultValuesConfigProvider() {
        return new DefaultValuesConfigProvider();
    }

    /**
     * Creates the Spring-backed configuration provider.
     *
     * @param environment the Spring environment
     * @param beanFactory the bean factory used to look up the non-candidate SDK defaults
     * @param properties the server properties
     * @return the configuration provider
     */
    @Bean(name = "a2aConfigProvider")
    @ConditionalOnMissingBean(A2AConfigProvider.class)
    public SpringA2AConfigProvider a2aConfigProvider(final Environment environment,
                                                     final BeanFactory beanFactory,
                                                     final A2AServerProperties properties) {
        final var defaults = beanFactory.getBean("a2aDefaultValuesConfigProvider",
                DefaultValuesConfigProvider.class);
        return new SpringA2AConfigProvider(environment, defaults, sdkOverrides(properties));
    }

    /**
     * Creates the default tenant-based authorization provider.
     *
     * @param scopeLocators the scope locator beans
     * @param storeProvider the store provider
     * @return the authorization provider
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskAuthorizationProvider tenantTaskAuthorizationProvider(
            final ObjectProvider<TaskScopeLocator> scopeLocators,
            final A2AStoreProvider storeProvider) {
        final var locator = scopeLocators.getIfUnique(
                () -> TaskScopeLocator.fromTaskStore(storeProvider.taskStore()));
        return new TenantTaskAuthorizationProvider(locator);
    }

    /**
     * Creates a default tenant executor router when multi-tenancy is enabled.
     *
     * @param executors the agent executor beans
     * @return the executor router
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = A2AServerProperties.PREFIX, name = "multitenancy.enabled", havingValue = "true")
    public AgentExecutorRouter agentExecutorRouter(final ObjectProvider<AgentExecutor> executors) {
        return new MapAgentExecutorRouter(defaultExecutor(executors), Map.of());
    }

    /**
     * Creates a default tenant card router when multi-tenancy is enabled.
     *
     * @param agentCard the default agent card
     * @return the card router
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = A2AServerProperties.PREFIX, name = "multitenancy.enabled", havingValue = "true")
    public AgentCardRouter agentCardRouter(final AgentCard agentCard) {
        return new MapAgentCardRouter(agentCard, Map.of());
    }

    /**
     * Creates the SDK request handler.
     *
     * @param executors the agent executor beans
     * @param executorRouters the optional tenant executor router
     * @param storeProvider the store provider
     * @param queueManager the queue manager
     * @param mainEventBusProcessor the event bus processor
     * @param agentExecutorPool the agent executor pool
     * @param eventConsumerPool the event consumer pool
     * @param configProvider the SDK configuration provider
     * @param authorizationProviders the optional authorization provider
     * @param customizers the request handler customizers
     * @param properties the server properties
     * @return the request handler
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultRequestHandler defaultRequestHandler(
            final ObjectProvider<AgentExecutor> executors,
            final ObjectProvider<AgentExecutorRouter> executorRouters,
            final A2AStoreProvider storeProvider,
            final QueueManager queueManager,
            final MainEventBusProcessor mainEventBusProcessor,
            @Qualifier("a2aAgentExecutor") final Executor agentExecutorPool,
            @Qualifier("a2aEventConsumerExecutor") final Executor eventConsumerPool,
            @Qualifier("a2aConfigProvider") final A2AConfigProvider configProvider,
            final ObjectProvider<TaskAuthorizationProvider> authorizationProviders,
            final ObjectProvider<A2AServerCustomizer> customizers,
            final A2AServerProperties properties) {
        final var router = executorRouters.getIfUnique();
        final var executor = router != null ? router.resolve(null) : defaultExecutor(executors);
        final var authorizationRequired = authorizationRequired(properties);
        final var builder = DefaultRequestHandler.builder()
                .agentExecutor(executor)
                .taskStore(storeProvider.taskStore())
                .queueManager(queueManager)
                .pushConfigStore(storeProvider.pushNotificationConfigStore())
                .mainEventBusProcessor(mainEventBusProcessor)
                .executor(agentExecutorPool)
                .eventConsumerExecutor(eventConsumerPool)
                .authorizationRequired(authorizationRequired)
                .pushNotificationsEnabled(properties.getPushNotifications().isEnabled())
                .populateReferredTasks(properties.isPopulateReferredTasks());
        final var authorizationProvider = authorizationProviders.getIfUnique();
        if (authorizationProvider != null && authorizationRequired) {
            builder.authorizationProvider(authorizationProvider);
        }
        if (router != null) {
            builder.agentExecutorRouter(router);
        }
        final var handler = builder.build();
        SdkA2AConfigInjector.setConfigProvider(handler, configProvider);
        if (authorizationProvider != null && authorizationRequired) {
            SdkA2AConfigInjector.setAuthorizationProvider(handler, authorizationProvider);
        }
        if (router != null) {
            SdkA2AConfigInjector.setAgentExecutorRouter(handler, router);
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(handler));
        return handler;
    }

    /**
     * Creates the JSON-RPC handler used by the HTTP transport.
     *
     * @param agentCard the agent card
     * @param requestHandler the SDK request handler
     * @param agentExecutorPool the agent executor pool
     * @param cardRouters the optional tenant card router
     * @return the JSON-RPC handler
     */
    @Bean
    @ConditionalOnMissingBean
    public JSONRPCHandler jsonRpcHandler(final AgentCard agentCard,
                                         final RequestHandler requestHandler,
                                         @Qualifier("a2aAgentExecutor") final Executor agentExecutorPool,
                                         final ObjectProvider<AgentCardRouter> cardRouters) {
        final var handler = new JSONRPCHandler(agentCard, requestHandler, agentExecutorPool);
        cardRouters.ifUnique(router -> SdkA2AConfigInjector.setAgentCardRouter(handler, router));
        return handler;
    }

    /**
     * Resolves whether SDK authorization is enforced.
     *
     * @param properties the server properties
     * @return {@code true} when authorization is required
     */
    public static boolean authorizationRequired(final A2AServerProperties properties) {
        final var configured = properties.getAuthorization().getRequired();
        if (configured != null && !configured.isBlank()) {
            final var value = configured.trim();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            if (!"auto".equalsIgnoreCase(value)) {
                throw new IllegalStateException("'" + A2AServerProperties.PREFIX
                        + ".authorization.required' must be 'auto', 'true' or 'false' but was '"
                        + value + "'.");
            }
        }
        final var securityPresent = ClassUtils.isPresent(SECURITY_CONTEXT_CLASS,
                A2AServerAutoConfiguration.class.getClassLoader());
        if (!securityPresent) {
            LOG.warn("Spring Security is not on the classpath; A2A task authorization is disabled. "
                    + "Set '{}.authorization.required=true' to enable it with a custom "
                    + "TaskAuthorizationProvider.", A2AServerProperties.PREFIX);
        }
        return securityPresent;
    }

    /**
     * Builds the SDK configuration overrides from the starter properties.
     *
     * @param properties the server properties
     * @return the SDK key/value overrides
     */
    public static Map<String, String> sdkOverrides(final A2AServerProperties properties) {
        final var blocking = properties.getBlocking();
        final var overrides = new LinkedHashMap<String, String>();
        overrides.put(AGENT_TIMEOUT_KEY, Long.toString(seconds(blocking.getAgentTimeout())));
        overrides.put(CONSUMPTION_TIMEOUT_KEY, Long.toString(seconds(blocking.getConsumptionTimeout())));
        overrides.put(RECONCILIATION_TIMEOUT_KEY, Long.toString(seconds(blocking.getReconciliationTimeout())));
        overrides.put(POPULATE_REFERRED_TASKS_KEY, Boolean.toString(properties.isPopulateReferredTasks()));
        overrides.put(AUTHORIZATION_REQUIRED_KEY, Boolean.toString(authorizationRequired(properties)));
        overrides.put(PUSH_NOTIFICATIONS_ENABLED_KEY,
                Boolean.toString(properties.getPushNotifications().isEnabled()));
        return Map.copyOf(overrides);
    }

    private static <T> T single(final ObjectProvider<T> provider, final String label) {
        final var candidates = provider.orderedStream().toList();
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple " + label + " beans found. Define a single "
                    + A2AStoreProvider.class.getSimpleName() + " bean to select the A2A stores explicitly.");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static AgentExecutor defaultExecutor(final ObjectProvider<AgentExecutor> executors) {
        final var candidates = executors.orderedStream().toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No AgentExecutor bean found. The A2A server requires "
                    + "exactly one org.a2aproject.sdk.server.agentexecution.AgentExecutor bean "
                    + "(for example by extending AbstractA2AAgentExecutor) or an AgentExecutorRouter bean.");
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple AgentExecutor beans found. Provide an "
                    + "AgentExecutorRouter bean (for example MapAgentExecutorRouter) that resolves the "
                    + "default executor, or mark the default executor bean @Primary.");
        }
        return candidates.get(0);
    }

    private static AgentSkill toSkill(final A2AServerProperties.Skill skill) {
        return AgentSkill.builder()
                .id(skill.getId())
                .name(skill.getName())
                .description(Objects.requireNonNullElse(skill.getDescription(), ""))
                .tags(new ArrayList<>(skill.getTags()))
                .examples(new ArrayList<>(skill.getExamples()))
                .inputModes(new ArrayList<>(skill.getInputModes()))
                .outputModes(new ArrayList<>(skill.getOutputModes()))
                .build();
    }

    private static String resolveAgentUrl(final A2AServerProperties.AgentCard card, final String path,
                                          final Environment environment) {
        final var configured = card.getUrl();
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured.trim());
        }
        final var port = environment.getProperty("server.port", "8080");
        return "http://localhost:" + port + path;
    }

    private static String stripTrailingSlash(final String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static long seconds(final Duration duration) {
        return Objects.isNull(duration) ? 0L : Math.max(0L, duration.toSeconds());
    }

    private static boolean isBlank(final String value) {
        return Objects.isNull(value) || value.isBlank();
    }
}
