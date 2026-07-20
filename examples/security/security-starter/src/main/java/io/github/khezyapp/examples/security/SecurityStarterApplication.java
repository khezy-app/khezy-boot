package io.github.khezyapp.examples.security;

import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableKhezyApiSecurity
public class SecurityStarterApplication {

    private SecurityStarterApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(SecurityStarterApplication.class, args);
    }
}
