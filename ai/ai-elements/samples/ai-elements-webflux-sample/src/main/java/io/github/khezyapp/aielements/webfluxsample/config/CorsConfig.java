package io.github.khezyapp.aielements.webfluxsample.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Allows the ai-elements frontend (dev server on {@code localhost:5173}) to call the
 * sample over CORS. Production apps should tighten these origins to their own UI host.
 */
@Configuration
public class CorsConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("*");
    }
}
