package io.github.khezyapp.api.security.oauth2;

import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.util.List;
import java.util.function.Function;

/**
 * Strategy that maps an {@link OAuth2Authorization} to a list of
 * {@link SqlParameterValue} for JDBC persistence. Implementations can
 * encrypt or transform token values before they are written to the database.
 */
public interface OAuth2AuthorizationParameterMapper extends Function<OAuth2Authorization, List<SqlParameterValue>> {
}
