package io.github.khezyapp.a2a.sampleserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MathAgentApplication {

    private MathAgentApplication() {
    }

    public static void main(final String[] args) {
        SpringApplication.run(MathAgentApplication.class, args);
    }
}
