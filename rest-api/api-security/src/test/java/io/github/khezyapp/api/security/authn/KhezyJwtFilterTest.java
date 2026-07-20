package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.FactorExtractor;
import io.github.khezyapp.api.security.token.TokenExtractor;
import io.github.khezyapp.api.security.token.TokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KhezyJwtFilterTest {

    private TokenExtractor tokenExtractor;
    private TokenParser tokenParser;
    private FactorExtractor factorExtractor;
    private UserDetailsService userDetailsService;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private KhezyJwtFilter filter;

    @BeforeEach
    void setUp() {
        tokenExtractor = mock(TokenExtractor.class);
        tokenParser = mock(TokenParser.class);
        factorExtractor = mock(FactorExtractor.class);
        userDetailsService = mock(UserDetailsService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        filter = new KhezyJwtFilter(tokenExtractor, tokenParser, factorExtractor, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWhenTokenIsValid() throws Exception {
        final var user = User.withUsername("user")
                .password("")
                .authorities("ROLE_USER")
                .build();
        final var parsedToken = new TokenParser.ParsedToken(
                "user",
                Map.of("factors", List.of("PASSWORD")),
                List.of("ROLE_USER")
        );
        when(tokenExtractor.extract(request)).thenReturn(Optional.of("valid-token"));
        when(tokenParser.parse("valid-token")).thenReturn(parsedToken);
        when(userDetailsService.loadUserByUsername("user")).thenReturn(user);
        when(factorExtractor.extractFactors(any())).thenReturn(List.of("FACTOR_PASSWORD"));

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user");
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER", "FACTOR_PASSWORD");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueChainWhenNoToken() throws Exception {
        when(tokenExtractor.extract(request)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenParser, userDetailsService, factorExtractor);
    }

    @Test
    void shouldHandleTokenParseFailure() throws Exception {
        when(tokenExtractor.extract(request)).thenReturn(Optional.of("bad-token"));
        when(tokenParser.parse("bad-token")).thenThrow(new TokenParser.TokenException("Invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleUserNotFoundFailure() throws Exception {
        final var parsedToken = new TokenParser.ParsedToken("unknown", java.util.Map.of(), List.of());
        when(tokenExtractor.extract(request)).thenReturn(Optional.of("token"));
        when(tokenParser.parse("token")).thenReturn(parsedToken);
        when(userDetailsService.loadUserByUsername("unknown"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleFactorExtractorFailure() throws Exception {
        final var parsedToken = new TokenParser.ParsedToken("user", java.util.Map.of(), List.of());
        when(tokenExtractor.extract(request)).thenReturn(Optional.of("token"));
        when(tokenParser.parse("token")).thenReturn(parsedToken);
        when(userDetailsService.loadUserByUsername("user"))
                .thenReturn(User.withUsername("user").password("").authorities("ROLE_USER").build());
        when(factorExtractor.extractFactors(any())).thenThrow(new RuntimeException("Factor extraction failed"));

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldHandleExtractionFailure() throws Exception {
        when(tokenExtractor.extract(request)).thenThrow(new RuntimeException("Extraction failed"));

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPreserveExistingSecurityContextOnFailure() throws Exception {
        final var existing = mock(Authentication.class);
        when(existing.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(existing);
        when(tokenExtractor.extract(request)).thenReturn(Optional.of("bad-token"));
        when(tokenParser.parse("bad-token")).thenThrow(new TokenParser.TokenException("Invalid"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(filterChain).doFilter(request, response);
    }
}
