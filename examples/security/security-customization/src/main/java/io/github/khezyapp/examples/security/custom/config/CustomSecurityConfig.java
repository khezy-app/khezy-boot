package io.github.khezyapp.examples.security.custom.config;

import io.github.khezyapp.api.security.authn.FactorAppendingConfigurer;
import io.github.khezyapp.api.security.authn.KhezyJwtFilter;
import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;
import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class CustomSecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        final var user = User.builder()
                .username("user")
                .password("{noop}user")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(List.of(user));
    }

    @Bean
    public SecurityFilterChain customSecurityFilterChain(
            final HttpSecurity http,
            final AccessDeniedHandler accessDeniedHandler,
            final AuthenticationEntryPoint authenticationEntryPoint,
            final ObjectProvider<KhezyJwtFilter> jwtFilterProvider,
            final ObjectProvider<AuthorizationManager<RequestAuthorizationContext>> authManagerProvider
    ) throws Exception {
        final var jwtFilter = jwtFilterProvider.getIfAvailable();
        final var authManager = authManagerProvider.getIfAvailable();

        var builder = http
                .with(new FactorAppendingConfigurer(), configurer -> { })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ec -> ec
                        .accessDeniedHandler(accessDeniedHandler)
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(req -> {
                    req.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    req.requestMatchers("/auth/**").permitAll();
                    if (Objects.nonNull(authManager)) {
                        req.requestMatchers("/secure/**").access(authManager);
                    } else {
                        req.requestMatchers("/secure/**").authenticated();
                    }
                    req.anyRequest().authenticated();
                });

        if (Objects.nonNull(jwtFilter)) {
            builder = builder.addFilterBefore(
                    jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return builder.build();
    }
}
