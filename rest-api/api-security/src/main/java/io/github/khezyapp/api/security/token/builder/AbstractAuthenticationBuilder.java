package io.github.khezyapp.api.security.token.builder;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base implementation of {@link AuthenticationBuilder} that stores principal, credentials,
 * details, and authorities in protected fields. Subclasses populate the fields and override
 * {@link #build()} to produce the concrete Spring Security {@link Authentication} type.
 *
 * @param <B> the concrete builder type returned by mutators (self-type pattern)
 */
@SuppressWarnings("unchecked")
public abstract class AbstractAuthenticationBuilder<B extends AbstractAuthenticationBuilder<B>>
        implements AuthenticationBuilder<B> {

    protected Object credentials;
    protected Object principal;
    protected Object details;
    protected Collection<GrantedAuthority> authorities;

    /**
     * Creates a builder pre-populated from an existing {@link Authentication}.
     * Copies the credentials, principal, authorities, and details from the given token.
     *
     * @param authentication the existing token to copy values from
     */
    protected AbstractAuthenticationBuilder(final Authentication authentication) {
        this.credentials = authentication.getCredentials();
        this.principal = authentication.getPrincipal();
        this.authorities = Objects.isNull(authentication.getAuthorities()) ?
                new ArrayList<>() :
                new ArrayList<>(authentication.getAuthorities());
        this.details = authentication.getDetails();
    }

    @Override
    public B principal(final Object principal) {
        this.principal = principal;
        return (B) this;
    }

    @Override
    public B details(final Object details) {
        this.details = details;
        return (B) this;
    }

    @Override
    public B authorities(final Collection<? extends GrantedAuthority> newAuthorities) {
        if (Objects.nonNull(newAuthorities)) {
            for (final var authority : newAuthorities) {
                if (!this.authorities.contains(authority)) {
                    this.authorities.add(authority);
                }
            }
        }
        return (B) this;
    }

    @Override
    public B authorities(final GrantedAuthority... newAuthorities) {
        return authorities(Arrays.asList(newAuthorities));
    }

    @Override
    public B authorities(final Consumer<Collection<GrantedAuthority>> authorities) {
        authorities.accept(this.authorities);
        return (B) this;
    }

    @Override
    public B credentials(final Object credentials) {
        this.credentials = credentials;
        return (B) this;
    }
}
