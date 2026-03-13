package io.github.khezyapp.api.exception.autoconfigure;

import io.github.khezyapp.api.exception.ValidationUtils;
import io.github.khezyapp.api.exception.controller.AuthExceptionAdviceController;
import io.github.khezyapp.api.exception.controller.CommonExceptionAdviceController;
import io.github.khezyapp.api.exception.controller.JJwtExceptionAdviceController;
import io.github.khezyapp.api.exception.logging.DefaultErrorLogger;
import io.github.khezyapp.api.exception.logging.ErrorLogger;
import io.github.khezyapp.api.exception.logging.ErrorLoggingProperties;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Validator;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring Boot auto-configuration for the Khezy Exception Handling library.
 * <p>
 * This class automatically registers the necessary beans for internationalized error messages,
 * validation utilities, and global exception handlers. It uses conditional annotations to
 * ensure that components are only loaded if they are missing from the application context
 * and if their required dependencies (like Spring Security or JJWT) are present on the classpath.
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ErrorLoggingProperties.class)
public class KhezyExceptionAutoConfiguration {

    /**
     * Configures a dedicated {@link MessageSource} for the library's exception messages.
     * <p>
     * It looks for a resource bundle named {@code ValidationMessages} on the classpath,
     * defaulting to UTF-8 encoding. This bean is qualified as {@code khezyI18nException}
     * to avoid conflicts with the primary application message source.
     * </p>
     *
     * @return the configured {@link MessageSource}
     */
    @Bean(name = "khezyI18nException")
    @ConditionalOnMissingBean(name = "khezyI18nException")
    public MessageSource khezyMessageSource(@Value("${spring.messages.basename:messages}") final String userBasenames) {
        final var messageSource = new ReloadableResourceBundleMessageSource();
        final var basenames = Stream.of(userBasenames.split(","))
                .map(String::strip)
                .map(path -> path.startsWith(":") ? path : "classpath:" + path)
                .toList()
                .toArray(new String[0]);

        final var finalPaths = new String[basenames.length + 1];
        System.arraycopy(basenames, 0, finalPaths, 0, basenames.length);
        finalPaths[finalPaths.length - 1] = "classpath:i18n/errors/KhezyValidationMessages";

        messageSource.setBasenames(finalPaths);
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        messageSource.setFallbackToSystemLocale(true);
        return messageSource;
    }

    @Bean
    public ValidationConfigurationCustomizer khezyValidationCustomizer(
            @Qualifier("khezyI18nException") final MessageSource messageSource) {
        return configuration -> {
            final var locator = new MessageSourceResourceBundleLocator(messageSource);
            configuration.messageInterpolator(new ResourceBundleMessageInterpolator(locator));
        };
    }

    /**
     * Registers a utility bean for programmatic validation tasks.
     *
     * @param validator the standard Jakarta {@link jakarta.validation.Validator}
     * @return a new {@link ValidationUtils} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ValidationUtils validationUtils(final ObjectProvider<Validator> validator) {
        return new ValidationUtils(validator.getIfAvailable());
    }

    /**
     * Registers the default error logging implementation.
     *
     * @param properties the bound {@link ErrorLoggingProperties}
     * @return a {@link DefaultErrorLogger} instance
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorLogger errorLogger(final ErrorLoggingProperties properties) {
        return new DefaultErrorLogger(properties);
    }

    /**
     * Registers the advisor for common exceptions.
     *
     * @param messageSource the library-specific message source
     * @param errorLogger   the configured logger
     * @return the common exception advice controller
     */
    @Bean
    @ConditionalOnMissingBean
    public CommonExceptionAdviceController commonExceptionAdviceController(
            final @Qualifier("khezyI18nException") MessageSource messageSource,
            final ErrorLogger errorLogger) {
        return new CommonExceptionAdviceController(messageSource, errorLogger);
    }

    /**
     * Nested configuration for Spring Security exception handling.
     * <p>
     * Only activated if {@link AuthenticationException} is detected on the classpath.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuthenticationException.class)
    static class SecurityAdviceConfiguration {

        /**
         * Registers the advisor for Security and Access Denied exceptions.
         *
         * @param messageSource the library-specific message source
         * @param errorLogger   the configured logger
         * @return the security exception advice controller
         */
        @Bean
        @ConditionalOnMissingBean
        public AuthExceptionAdviceController authExceptionAdviceController(
                final @Qualifier("khezyI18nException") MessageSource messageSource,
                final ErrorLogger errorLogger) {
            return new AuthExceptionAdviceController(messageSource, errorLogger);
        }
    }

    /**
     * Nested configuration for JJWT exception handling.
     * <p>
     * Only activated if {@link JwtException} is detected on the classpath.
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JwtException.class)
    static class JwtAdviceConfiguration {

        /**
         * Registers the advisor for JWT-specific failures (expiration, signature, etc.).
         *
         * @param messageSource the library-specific message source
         * @param errorLogger   the configured logger
         * @return the JWT exception advice controller
         */
        @Bean
        @ConditionalOnMissingBean
        public JJwtExceptionAdviceController jJwtExceptionAdviceController(
                final @Qualifier("khezyI18nException") MessageSource messageSource,
                final ErrorLogger errorLogger) {
            return new JJwtExceptionAdviceController(messageSource, errorLogger);
        }
    }
}
