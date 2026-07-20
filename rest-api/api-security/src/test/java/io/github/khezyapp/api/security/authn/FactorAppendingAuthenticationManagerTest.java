package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.AuthenticationBuilderManager;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FactorAppendingAuthenticationManagerTest {

    @Test
    void shouldDelegateAndAppendFactorAuthorities() {
        final var inputAuth = mock(Authentication.class);
        final var authResult = mock(Authentication.class);
        final var finalResult = mock(Authentication.class);

        final var delegate = mock(AuthenticationManager.class);
        when(delegate.authenticate(inputAuth)).thenReturn(authResult);

        final var builderManager = mock(AuthenticationBuilderManager.class);
        when(builderManager.build(any(), any())).thenReturn(finalResult);

        final var manager = new FactorAppendingAuthenticationManager(delegate, builderManager);
        final var result = manager.authenticate(inputAuth);

        assertThat(result).isSameAs(finalResult);
        verify(delegate).authenticate(inputAuth);
        verify(builderManager).build(eq(authResult), any());
    }

    @Test
    void shouldHandleNullCurrentAuthentication() {
        final var inputAuth = mock(Authentication.class);
        final var authResult = mock(Authentication.class);
        when(authResult.getAuthorities()).thenReturn(null);

        final var delegate = mock(AuthenticationManager.class);
        when(delegate.authenticate(inputAuth)).thenReturn(authResult);

        final var builderManager = mock(AuthenticationBuilderManager.class);
        when(builderManager.build(any(), any())).thenReturn(authResult);

        final var manager = new FactorAppendingAuthenticationManager(delegate, builderManager);
        final var result = manager.authenticate(inputAuth);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldMergeCurrentContextAuthorities() {
        final var inputAuth = mock(Authentication.class);
        final var authResult = mock(Authentication.class);

        final var delegate = mock(AuthenticationManager.class);
        when(delegate.authenticate(inputAuth)).thenReturn(authResult);

        final var builderManager = mock(AuthenticationBuilderManager.class);
        final var manager = new FactorAppendingAuthenticationManager(delegate, builderManager);

        manager.authenticate(inputAuth);

        verify(builderManager).build(eq(authResult), any());
    }
}
