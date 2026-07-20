package io.github.khezyapp.api.security.token;

import io.github.khezyapp.api.security.token.builder.AuthenticationBuilder;
import io.github.khezyapp.api.security.token.factory.AuthenticationBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthenticationBuilderManagerTest {

    @Test
    void shouldFindMatchingFactoryAndApplyBuilder() {
        final var auth = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final var factory = mock(AuthenticationBuilderFactory.class);
        final var builder = mock(AuthenticationBuilder.class);
        final var result = mock(Authentication.class);

        when(factory.supports(auth.getClass())).thenReturn(true);
        when(factory.create(auth)).thenReturn(builder);
        when(builder.build()).thenReturn(result);
        when(builder.authorities(any(Consumer.class))).thenReturn(builder);
        when(builder.authorities((Collection<? extends GrantedAuthority>) any())).thenReturn(builder);
        when(builder.addFactorAuthority()).thenReturn(builder);

        final var manager = new AuthenticationBuilderManager(List.of(factory));
        final var built = manager.build(auth, b -> b.authorities(List.of()).addFactorAuthority());

        assertThat(built).isSameAs(result);
        verify(factory).supports(auth.getClass());
        verify(factory).create(auth);
        verify(builder).build();
    }

    @Test
    void shouldReturnOriginalAuthWhenNoFactoryFound() {
        final var auth = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final var factory = mock(AuthenticationBuilderFactory.class);
        when(factory.supports(any())).thenReturn(false);

        final var manager = new AuthenticationBuilderManager(List.of(factory));
        final var result = manager.build(auth, b -> { });

        assertThat(result).isSameAs(auth);
    }

    @Test
    void shouldReturnNullWhenNullAuthPassed() {
        final var factory = mock(AuthenticationBuilderFactory.class);
        final var manager = new AuthenticationBuilderManager(List.of(factory));

        final var result = manager.build(null, b -> { });

        assertThat(result).isNull();
    }

    @Test
    void shouldTryFactoriesInOrder() {
        final var auth = UsernamePasswordAuthenticationToken.unauthenticated("user", "pass");
        final var factory1 = mock(AuthenticationBuilderFactory.class);
        final var factory2 = mock(AuthenticationBuilderFactory.class);
        final var authBuilder = mock(AuthenticationBuilder.class);
        when(factory2.supports(auth.getClass())).thenReturn(true);
        when(factory2.create(auth)).thenReturn(authBuilder);
        when(authBuilder.build()).thenReturn(mock(Authentication.class));

        final var manager = new AuthenticationBuilderManager(List.of(factory1, factory2));
        manager.build(auth, b -> { });

        verify(factory1).supports(auth.getClass());
        verify(factory2).supports(auth.getClass());
        verify(factory1, never()).create(any());
        verify(factory2).create(auth);
    }
}
