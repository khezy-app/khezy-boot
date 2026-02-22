package io.github.khezyapp.jooqspec;

import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfigurationCustomizer jooqCustomizer() {
        return config -> {
            final var settings = config.settings();
            settings.setRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_UNQUOTED);
            config.setSettings(settings);
        };
    }
}
