package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import io.github.khezyapp.a2a.starter.A2AAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the A2A HTTP endpoints (agent card discovery + JSON-RPC message/task
 * routes) once a {@link A2ARequestDispatcher} bean exists in a servlet web
 * application. Controllers depend on the dispatcher only — never on SDK server
 * runtime classes.
 */
@AutoConfiguration(after = A2AAutoConfiguration.class)
@ConditionalOnClass({A2ARequestDispatcher.class, ObjectMapper.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(A2ARequestDispatcher.class)
public class A2AWebAutoConfiguration {

    @Bean
    AgentCardController agentCardController(final A2ARequestDispatcher dispatcher,
                                            final CallerIdentityResolver identityResolver) {
        return new AgentCardController(dispatcher, identityResolver);
    }

    @Bean
    MessageController messageController(final A2ARequestDispatcher dispatcher,
                                        final CallerIdentityResolver identityResolver,
                                        final ObjectMapper objectMapper) {
        return new MessageController(dispatcher, identityResolver, objectMapper);
    }

    @Bean
    TaskController taskController(final A2ARequestDispatcher dispatcher,
                                  final CallerIdentityResolver identityResolver,
                                  final ObjectMapper objectMapper) {
        return new TaskController(dispatcher, identityResolver, objectMapper);
    }

    @Bean
    SdkA2AJsonRpcController sdkA2AJsonRpcController(final A2ARequestDispatcher dispatcher,
                                                    final CallerIdentityResolver identityResolver) {
        return new SdkA2AJsonRpcController(dispatcher, identityResolver);
    }
}
