package io.github.khezyapp.api.security.oauth2;

import io.github.khezyapp.api.security.crypto.CustomEncryptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Factory that produces parameter mappers and row mappers for
 * {@link JdbcOAuth2AuthorizedClientService} with transparent encryption and
 * decryption of OAuth2 client tokens. Access tokens and refresh tokens are
 * encrypted before being persisted and decrypted when read back from the
 * database.
 */
public class EncryptedOAuth2ClientMappers {
    private final CustomEncryptor encryptor;
    private final String passwordVersion;
    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * Creates a new mapper factory.
     *
     * @param encryptor                  the encryptor used for token encryption/decryption
     * @param passwordVersion            the version label passed to the encryptor
     * @param repository                 the client registration repository for resolving registrations
     */
    public EncryptedOAuth2ClientMappers(final CustomEncryptor encryptor,
                                        final String passwordVersion,
                                        final ClientRegistrationRepository repository) {
        this.encryptor = encryptor;
        this.passwordVersion = passwordVersion;
        this.clientRegistrationRepository = repository;
    }

    /**
     * Returns a parameter mapper that encrypts access and refresh tokens
     * before persisting an {@link OAuth2AuthorizedClient} via JDBC.
     *
     * @return the encrypting parameter mapper
     */
    public OAuth2AuthorizedClientParameterMapper getParametersMapper() {
        return holder -> {
            final var client = holder.getAuthorizedClient();
            final var parameters = new ArrayList<SqlParameterValue>();

            parameters.add(new SqlParameterValue(Types.VARCHAR, client.getClientRegistration().getRegistrationId()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, holder.getPrincipal().getName()));
            parameters.add(new SqlParameterValue(Types.VARCHAR, client.getAccessToken().getTokenType().getValue()));

            // --- ENCRYPT ACCESS TOKEN ---
            final var plainAccessToken = client.getAccessToken().getTokenValue();
            final var encryptedAccessToken = encryptor.encrypt(plainAccessToken, plainAccessToken)
                    .getBytes(StandardCharsets.UTF_8);
            parameters.add(new SqlParameterValue(Types.BLOB, encryptedAccessToken));

            parameters.add(new SqlParameterValue(Types.TIMESTAMP, Timestamp.from(client.getAccessToken().getIssuedAt())));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, Timestamp.from(client.getAccessToken().getExpiresAt())));

            final var scopes = CollectionUtils.isEmpty(client.getAccessToken().getScopes()) ? null :
                    StringUtils.collectionToDelimitedString(client.getAccessToken().getScopes(), ",");
            parameters.add(new SqlParameterValue(Types.VARCHAR, scopes));

            // --- ENCRYPT REFRESH TOKEN (IF EXISTS) ---
            var encryptedRefreshTokenBytes = new byte[0];
            Timestamp refreshTokenIssuedAt = null;
            if (client.getRefreshToken() != null) {
                final var plainRefreshToken = client.getRefreshToken().getTokenValue();
                encryptedRefreshTokenBytes = encryptor.encrypt(passwordVersion, plainRefreshToken)
                        .getBytes(StandardCharsets.UTF_8);
                if (Objects.nonNull(client.getRefreshToken().getIssuedAt())) {
                    refreshTokenIssuedAt = Timestamp.from(client.getRefreshToken().getIssuedAt());
                }
            }
            parameters.add(new SqlParameterValue(Types.BLOB, encryptedRefreshTokenBytes));
            parameters.add(new SqlParameterValue(Types.TIMESTAMP, refreshTokenIssuedAt));

            return parameters;
        };
    }

    /**
     * Returns a row mapper that decrypts encrypted access and refresh tokens
     * when an {@link OAuth2AuthorizedClient} is read from the database via JDBC.
     *
     * @return the decrypting row mapper
     */
    public RowMapper<OAuth2AuthorizedClient> getRowMapper() {
        final var defaultRowMapper =
                new JdbcOAuth2AuthorizedClientService.OAuth2AuthorizedClientRowMapper(clientRegistrationRepository);

        return (rs, rowNum) -> {
            // Let the default mapper build the object initially
            final var client = defaultRowMapper.mapRow(rs, rowNum);
            if (Objects.isNull(client)) {
                return null;
            }

            // Extract the encrypted database values
            final var dbEncryptedAccess = rs.getBytes("access_token_value");
            final var plainAccess = encryptor.decrypt(new String(dbEncryptedAccess, StandardCharsets.UTF_8));

            String plainRefresh = null;
            final var refreshBytes = rs.getBytes("refresh_token_value");
            if (Objects.nonNull(refreshBytes)) {
                plainRefresh = encryptor.decrypt(new String(refreshBytes, StandardCharsets.UTF_8));
            }

            // Reconstruct a brand new OAuth2AuthorizedClient with decrypted values
            return new OAuth2AuthorizedClient(
                    client.getClientRegistration(),
                    client.getPrincipalName(),
                    new OAuth2AccessToken(
                            client.getAccessToken().getTokenType(),
                            plainAccess,
                            client.getAccessToken().getIssuedAt(),
                            client.getAccessToken().getExpiresAt(),
                            client.getAccessToken().getScopes()
                    ),
                    plainRefresh == null ? null : new OAuth2RefreshToken(
                            plainRefresh,
                            client.getRefreshToken().getIssuedAt()
                    )
            );
        };
    }
}
