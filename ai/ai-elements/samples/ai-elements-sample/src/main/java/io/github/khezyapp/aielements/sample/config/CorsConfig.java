package io.github.khezyapp.aielements.sample.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the ai-elements frontend (dev server on {@code localhost:5173}) to call the
 * sample over CORS. Production apps should tighten these origins to their own UI host.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("*");
    }
}
