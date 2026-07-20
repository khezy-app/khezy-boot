package io.github.khezyapp.api.security.token.builder;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Builder for {@link JwtAuthenticationToken}.
 * Preserves the original JWT and name from the source token while allowing authority and details changes.
 *
 * @param <B> the concrete builder type returned by mutators
 */
public class JwtTokenAuthenticationBuilder<B extends JwtTokenAuthenticationBuilder<B>>
        extends AbstractAuthenticationBuilder<B> {
    private final String name;
    private final Jwt token;

    /**
     * Creates a builder from an existing {@link JwtAuthenticationToken}.
     *
     * @param authentication the existing token to copy values from
     */
    public JwtTokenAuthenticationBuilder(final JwtAuthenticationToken authentication) {
        super(authentication);
        this.name = authentication.getName();
        this.token = authentication.getToken();
    }

    @Override
    public JwtAuthenticationToken build() {
        final var jwtToken = new JwtAuthenticationToken(this.token, this.authorities, this.name);
        jwtToken.setDetails(this.details);
        return jwtToken;
    }
}
