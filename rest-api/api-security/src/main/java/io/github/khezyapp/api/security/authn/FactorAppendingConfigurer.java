package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

/**
 * A Spring Security {@link AbstractHttpConfigurer} that replaces the default
 * {@link org.springframework.security.authentication.AuthenticationManager}
 * with a {@link FactorAppendingAuthenticationManager}
 * during {@code HttpSecurity} initialisation. Wired automatically when the
 * {@code KhezySecurityAutoConfiguration} is active.
 */
@Slf4j
public class FactorAppendingConfigurer extends AbstractHttpConfigurer<FactorAppendingConfigurer, HttpSecurity> {

    /**
     * Builds the default {@link org.springframework.security.authentication.AuthenticationManager}, wraps it in a
     * {@link FactorAppendingAuthenticationManager}, and registers it on
     * the {@link HttpSecurity} instance.
     */
    @Override
    public void init(final HttpSecurity http) throws Exception {
        log.info("Initializing FactorAppendingConfigurer: Appending custom multi-factor authentication steps.");

        final var context = http.getSharedObject(ApplicationContext.class);
        final var authConfig = context.getBean(AuthenticationConfiguration.class);
        final var defaultManager = authConfig.getAuthenticationManager();

        final var builderManager = context.getBean(AuthenticationBuilderManager.class);

        if (log.isDebugEnabled()) {
            log.debug("Wiring FactorAppendingAuthenticationManager. "
                            + "Found default manager: [{}], extracted builder manager: [{}]",
                    defaultManager.getClass().getSimpleName(),
                    builderManager.getClass().getName());
        }

        final var customManager = new FactorAppendingAuthenticationManager(defaultManager, builderManager);
        http.authenticationManager(customManager);

        log.debug("Successfully injected FactorAppendingAuthenticationManager into HttpSecurity context.");
    }
}
