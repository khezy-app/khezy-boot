package io.github.khezyapp.examples.security.rowlevel;

import io.github.khezyapp.api.security.autoconfigure.annotation.EnableKhezyApiSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableKhezyApiSecurity
public class RowLevelApplication {

    private RowLevelApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(RowLevelApplication.class, args);
    }
}
