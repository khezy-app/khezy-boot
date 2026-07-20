package io.github.khezyapp.examples.security.mfa.repository;

import io.github.khezyapp.api.security.authority.RequiredFactorAuthoritiesRepository;
import io.github.khezyapp.api.security.authz.InMemoryRequiredFactorRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class UserMfaConfig {

    @Bean
    public RequiredFactorAuthoritiesRepository requiredFactorAuthoritiesRepository() {
        return new InMemoryRequiredFactorRepository(Map.of(
                "admin", List.of("FACTOR_PASSWORD", "FACTOR_WEBAUTHN")
        ));
    }
}
