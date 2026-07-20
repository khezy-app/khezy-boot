package io.github.khezyapp.examples.security.context.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    UserDetailsService userDetailsService() {
        final var alice = User.builder()
                .username("alice")
                .password("{noop}alice")
                .roles("USER")
                .build();
        final var bob = User.builder()
                .username("bob")
                .password("bob")
                .roles("{noop}USER")
                .build();
        final var charlie = User.builder()
                .username("charlie")
                .password("{noop}charlie")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(List.of(alice, bob, charlie));
    }
}
