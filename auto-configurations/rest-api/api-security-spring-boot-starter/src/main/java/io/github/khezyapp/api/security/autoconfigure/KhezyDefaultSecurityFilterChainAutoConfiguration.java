package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.authn.FactorAppendingConfigurer;
import io.github.khezyapp.api.security.authn.KhezyJwtFilter;
import io.github.khezyapp.api.security.autoconfigure.properties.KhezySecurityProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Objects;

/**
 * Auto-configuration for the default {@link SecurityFilterChain}.
 * Only active when the user hasn't defined their own {@code SecurityFilterChain} bean.
 * Applies {@link FactorAppendingConfigurer} for MFA support.
 */
@AutoConfiguration
@ConditionalOnMissingBean(SecurityFilterChain.class)
@ConditionalOnProperty(prefix = "khezy.api.security", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(KhezySecurityProperties.class)
public class KhezyDefaultSecurityFilterChainAutoConfiguration {

    @Bean
    SecurityFilterChain defaultSecurityFilterChain(
            final HttpSecurity http,
            final AccessDeniedHandler accessDeniedHandler,
            final AuthenticationEntryPoint authenticationEntryPoint,
            final ObjectProvider<KhezyJwtFilter> jwtFilterProvider,
            final ObjectProvider<AuthorizationManager<RequestAuthorizationContext>> authManagerProvider,
            final ObjectProvider<CorsConfigurationSource> corsProvider,
            final KhezySecurityProperties properties) throws Exception {

        final var jwtFilter = jwtFilterProvider.getIfAvailable();
        final var cors = corsProvider.getIfAvailable();
        final var authManager = authManagerProvider.getIfAvailable();

        var builder = http
                .with(new FactorAppendingConfigurer(), configurer -> { })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(properties.getSessionCreationPolicy()))
                .exceptionHandling(ec -> ec
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeHttpRequests(req -> {
                    req.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    if (properties.isPermitAll()) {
                        req.anyRequest().permitAll();
                    } else {
                        for (final var pattern : properties.getPermitPatterns()) {
                            req.requestMatchers(pattern).permitAll();
                        }
                        if (Objects.nonNull(authManager)) {
                            req.anyRequest().access(authManager);
                        } else {
                            req.anyRequest().authenticated();
                        }
                    }
                });

        if (Objects.nonNull(cors)) {
            builder = builder.cors(c -> c.configurationSource(cors));
        }
        if (Objects.nonNull(jwtFilter)) {
            builder = builder.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return builder.build();
    }
}
