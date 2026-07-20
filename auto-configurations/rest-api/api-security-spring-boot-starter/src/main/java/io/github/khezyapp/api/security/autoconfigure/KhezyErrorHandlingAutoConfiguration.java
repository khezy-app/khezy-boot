package io.github.khezyapp.api.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.khezyapp.api.security.authn.RestApiAuthenticationEntryPoint;
import io.github.khezyapp.api.security.authz.RestApiAccessDeniedHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Auto-configuration for REST API error handling.
 * Provides structured JSON error responses for 401 and 403.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class KhezyErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    RestApiAccessDeniedHandler restApiAccessDeniedHandler(final ObjectMapper objectMapper) {
        return new RestApiAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    RestApiAuthenticationEntryPoint restApiAuthenticationEntryPoint(final ObjectMapper objectMapper) {
        return new RestApiAuthenticationEntryPoint(objectMapper);
    }
}
