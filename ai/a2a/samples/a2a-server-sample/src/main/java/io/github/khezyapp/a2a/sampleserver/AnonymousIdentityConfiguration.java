package io.github.khezyapp.a2a.sampleserver;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The starter's {@code callerIdentityResolver} bean is gated on Spring Security being
 * present, and this sample deliberately ships without it — so it contributes its own
 * anonymous resolver here (the exact override point the starter documents).
 */
@Configuration
public class AnonymousIdentityConfiguration {

    @Bean
    CallerIdentityResolver callerIdentityResolver() {
        return CallerIdentity::anonymous;
    }
}
