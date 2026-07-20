package io.github.khezyapp.api.security.autoconfigure;

import io.github.khezyapp.api.security.authn.KhezyJwtFilter;
import io.github.khezyapp.api.security.autoconfigure.properties.KhezyJwtProperties;
import io.github.khezyapp.api.security.token.FactorExtractor;
import io.github.khezyapp.api.security.token.TokenExtractor;
import io.github.khezyapp.api.security.token.TokenParser;
import io.github.khezyapp.api.security.token.extractor.BearerTokenExtractor;
import io.github.khezyapp.api.security.token.extractor.ClaimBasedFactorExtractor;
import io.github.khezyapp.api.security.token.parser.JwtTokenParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Auto-configuration for JWT authentication. Activated when
 * {@code khezy.api.security.jwt.secret} property is set.
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.jsonwebtoken.Claims")
@ConditionalOnProperty(prefix = "khezy.api.security.jwt", name = "secret")
@EnableConfigurationProperties(KhezyJwtProperties.class)
public class KhezyJwtFilterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TokenParser tokenParser(final KhezyJwtProperties properties) {
        return new JwtTokenParser(properties.getSecret(), properties.getIssuer());
    }

    @Bean
    @ConditionalOnMissingBean
    TokenExtractor tokenExtractor() {
        return new BearerTokenExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    FactorExtractor factorExtractor(final KhezyJwtProperties properties) {
        return new ClaimBasedFactorExtractor(properties.getFactorsClaim());
    }

    @Bean
    @ConditionalOnMissingBean(KhezyJwtFilter.class)
    KhezyJwtFilter khezyJwtFilter(final TokenExtractor tokenExtractor,
                                  final TokenParser tokenParser,
                                  final FactorExtractor factorExtractor,
                                  final UserDetailsService userDetailsService) {
        return new KhezyJwtFilter(tokenExtractor, tokenParser, factorExtractor, userDetailsService);
    }
}
