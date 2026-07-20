package io.github.khezyapp.examples.security.mfa;

import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableKhezyApiSecurity(mfAuthorities = {"FACTOR_PASSWORD", "FACTOR_WEBAUTHN"})
public class SecurityMfaApplication {

    private SecurityMfaApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(SecurityMfaApplication.class, args);
    }
}
