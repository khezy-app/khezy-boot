package io.github.khezyapp.examples.security.context;

import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableKhezyApiSecurity
public class SecurityContextApplication {

    private SecurityContextApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(SecurityContextApplication.class, args);
    }
}
