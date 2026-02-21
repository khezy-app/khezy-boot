package io.github.khezyapp.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ExampleJpaSpecApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ExampleJpaSpecApplication.class, args);
    }
}
