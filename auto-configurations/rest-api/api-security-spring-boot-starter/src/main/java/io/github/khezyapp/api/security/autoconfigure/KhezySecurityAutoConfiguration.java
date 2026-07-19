package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.autoconfigure.properties.KhezyCorsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties({
        KhezyCorsProperties.class
})
@Import(value = {
        KhezyApiSecurityConfig.class,
        AuthenticationBuilderFactoryConfig.class
})
public class KhezySecurityAutoConfiguration {

}
