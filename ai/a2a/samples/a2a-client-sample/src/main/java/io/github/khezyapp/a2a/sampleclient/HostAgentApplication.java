package io.github.khezyapp.a2a.sampleclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HostAgentApplication {

    private HostAgentApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(HostAgentApplication.class, args);
    }
}
