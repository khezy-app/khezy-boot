package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import static org.mockito.Mockito.*;

class FactorAppendingConfigurerTest {

    @Test
    void shouldInitWithAuthenticationConfiguration() throws Exception {
        final var http = mock(HttpSecurity.class);
        final var context = mock(ApplicationContext.class);
        final var authConfig = mock(AuthenticationConfiguration.class);
        final var authManager = mock(AuthenticationManager.class);
        final var builderManager = mock(AuthenticationBuilderManager.class);

        when(http.getSharedObject(ApplicationContext.class)).thenReturn(context);
        when(context.getBean(AuthenticationConfiguration.class)).thenReturn(authConfig);
        when(authConfig.getAuthenticationManager()).thenReturn(authManager);
        when(context.getBean(AuthenticationBuilderManager.class)).thenReturn(builderManager);

        final var configurer = new FactorAppendingConfigurer();
        configurer.init(http);

        verify(authConfig).getAuthenticationManager();
        verify(http).authenticationManager(any(FactorAppendingAuthenticationManager.class));
    }

    @Test
    void shouldNotCallAuthenticationManagerBuilderBuild() throws Exception {
        final var http = mock(HttpSecurity.class);
        final var context = mock(ApplicationContext.class);
        final var authConfig = mock(AuthenticationConfiguration.class);

        when(http.getSharedObject(ApplicationContext.class)).thenReturn(context);
        when(context.getBean(AuthenticationConfiguration.class)).thenReturn(authConfig);

        final var configurer = new FactorAppendingConfigurer();
        configurer.init(http);

        verify(authConfig, times(1)).getAuthenticationManager();
    }

    @Test
    void shouldWrapDefaultManager() throws Exception {
        final var http = mock(HttpSecurity.class);
        final var context = mock(ApplicationContext.class);
        final var authConfig = mock(AuthenticationConfiguration.class);
        final var authManager = mock(AuthenticationManager.class);
        final var builderManager = mock(AuthenticationBuilderManager.class);

        when(http.getSharedObject(ApplicationContext.class)).thenReturn(context);
        when(context.getBean(AuthenticationConfiguration.class)).thenReturn(authConfig);
        when(authConfig.getAuthenticationManager()).thenReturn(authManager);
        when(context.getBean(AuthenticationBuilderManager.class)).thenReturn(builderManager);

        final var configurer = new FactorAppendingConfigurer();
        configurer.init(http);

        verify(http).authenticationManager(argThat(manager ->
                manager instanceof FactorAppendingAuthenticationManager
        ));
    }
}
