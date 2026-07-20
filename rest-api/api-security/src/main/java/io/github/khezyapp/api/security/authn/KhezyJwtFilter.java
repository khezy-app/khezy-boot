package io.github.khezyapp.api.security.authn;

import io.github.khezyapp.api.security.token.FactorExtractor;
import io.github.khezyapp.api.security.token.TokenExtractor;
import io.github.khezyapp.api.security.token.TokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A default {@link OncePerRequestFilter} that extracts a token from the request,
 * parses it, and populates the {@link SecurityContextHolder} with the resulting
 * authentication and authorities (including MFA factors).
 * <p>
 * Auto-configured when {@code khezy.api.security.jwt.secret} property is set.
 * Override by defining your own {@code OncePerRequestFilter} bean.
 */
@RequiredArgsConstructor
public class KhezyJwtFilter extends OncePerRequestFilter {

    private final TokenExtractor tokenExtractor;
    private final TokenParser tokenParser;
    private final FactorExtractor factorExtractor;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        try {
            final var token = tokenExtractor.extract(request);
            if (token.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            final var parsedToken = tokenParser.parse(token.get());
            final var user = userDetailsService.loadUserByUsername(parsedToken.subject());

            final var authorities = new ArrayList<SimpleGrantedAuthority>();
            parsedToken.grantedAuthorities().forEach(
                    auth -> authorities.add(new SimpleGrantedAuthority(auth))
            );

            final var factors = factorExtractor.extractFactors(parsedToken.claims());
            factors.forEach(
                    factor -> authorities.add(new SimpleGrantedAuthority(factor))
            );

            final var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            final var emptyContext = SecurityContextHolder.createEmptyContext();
            emptyContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(emptyContext);
        } catch (final Exception e) {
            logger.debug("JWT authentication failed", e);
        }

        filterChain.doFilter(request, response);
    }
}
