package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.authn.FactorAppendingAuthenticationManager;
import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

/**
 * Auto-configuration that exposes {@link FactorAppendingAuthenticationManager}
 * as an injectable bean. This allows users with custom {@code SecurityFilterChain}
 * to inject and use the factor-appending authentication manager.
 */
@AutoConfiguration
public class KhezyFactorAppendingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FactorAppendingAuthenticationManager.class)
    FactorAppendingAuthenticationManager factorAppendingAuthenticationManager(
            final AuthenticationConfiguration authConfig,
            final AuthenticationBuilderManager builderManager) throws Exception {
        return new FactorAppendingAuthenticationManager(
                authConfig.getAuthenticationManager(), builderManager
        );
    }
}
