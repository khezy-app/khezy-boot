package io.github.khezyapp.api.audit.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(KhezyAuditProperties.class)
@Import(KhezyAuditLogMainAutoConfiguration.class)
public class KhezyAuditAutoConfiguration {
}
