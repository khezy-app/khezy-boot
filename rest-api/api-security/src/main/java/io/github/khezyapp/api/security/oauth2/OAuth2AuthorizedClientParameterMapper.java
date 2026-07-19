package io.github.khezyapp.api.security.oauth2;

import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;

import java.util.List;
import java.util.function.Function;

/**
 * Strategy that maps an {@code OAuth2AuthorizedClientHolder} to a list of
 * {@link SqlParameterValue} for JDBC persistence. Implementations that need
 * to encrypt or transform tokens before writing them to the database should
 * implement this interface.
 */
public interface OAuth2AuthorizedClientParameterMapper extends
        Function<JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientHolder, List<SqlParameterValue>> {
}
