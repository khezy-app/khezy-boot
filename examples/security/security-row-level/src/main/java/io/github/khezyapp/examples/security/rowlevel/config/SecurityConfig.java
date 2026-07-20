package io.github.khezyapp.examples.security.rowlevel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        final var alice = User.builder()
                .username("alice")
                .password("{noop}alice")
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(List.of(alice));
    }
}
