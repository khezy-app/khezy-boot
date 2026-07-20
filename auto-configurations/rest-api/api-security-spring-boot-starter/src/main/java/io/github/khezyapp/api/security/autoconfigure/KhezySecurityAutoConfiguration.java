package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.autoconfigure.properties.KhezyCorsProperties;
import io.github.khezyapp.api.security.autoconfigure.properties.KhezyJwtProperties;
import io.github.khezyapp.api.security.autoconfigure.properties.KhezySecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties({
        KhezyCorsProperties.class,
        KhezySecurityProperties.class,
        KhezyJwtProperties.class
})
@Import(value = {
        KhezyApiSecurityConfig.class,
        AuthenticationBuilderFactoryConfig.class
})
public class KhezySecurityAutoConfiguration {

}
