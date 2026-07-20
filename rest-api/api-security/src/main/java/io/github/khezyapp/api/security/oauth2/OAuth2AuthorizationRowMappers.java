package io.github.khezyapp.api.security.oauth2;

import io.github.khezyapp.api.security.crypto.CustomEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Objects;

/**
 * Factory that produces parameter mappers and row mappers for
 * {@link JdbcOAuth2AuthorizationService} with transparent encryption and
 * decryption of OAuth2 tokens. Each token value (access token, refresh token,
 * OIDC ID token, user code, device code) is encrypted before being written
 * to the database and decrypted when read back.
 */
@RequiredArgsConstructor
public class OAuth2AuthorizationRowMappers {
    private final CustomEncryptor encryptor;
    private final RegisteredClientRepository registeredClientRepository;
    private final String passwordVersion;

    /**
     * Returns a parameter mapper that encrypts all token values inside an
     * {@link OAuth2Authorization} before they are persisted via JDBC.
     *
     * @return the encrypting parameter mapper
     */
    public OAuth2AuthorizationParameterMapper getParameterMapper() {
        final var defaultParameterMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        return authorization -> {
            final var builder = OAuth2Authorization.from(authorization);

            final var accessToken = authorization.getAccessToken();
            final var refreshToken = authorization.getRefreshToken();
            final var oidcIdToken = authorization.getToken(OidcIdToken.class);
            final var oauth2UserCode = authorization.getToken(OAuth2UserCode.class);
            final var oauth2DeviceCode = authorization.getToken(OAuth2DeviceCode.class);

            if (Objects.nonNull(accessToken)) {
                builder.accessToken(encrypt(accessToken.getToken()));
            }
            if (Objects.nonNull(refreshToken)) {
                builder.refreshToken(encrypt(refreshToken.getToken()));
            }
            if (Objects.nonNull(oidcIdToken)) {
                builder.token(
                        encrypt(oidcIdToken.getToken()),
                        c -> c.putAll(oidcIdToken.getMetadata())
                );
            }
            if (Objects.nonNull(oauth2UserCode)) {
                builder.token(
                        encrypt(oauth2UserCode.getToken()),
                        c -> c.putAll(oauth2UserCode.getMetadata())
                );
            }
            if (Objects.nonNull(oauth2DeviceCode)) {
                builder.token(
                        encrypt(oauth2DeviceCode.getToken()),
                        c -> c.putAll(oauth2DeviceCode.getMetadata())
                );
            }

            return defaultParameterMapper.apply(builder.build());
        };
    }

    /**
     * Returns a row mapper that decrypts all encrypted token values when an
     * {@link OAuth2Authorization} is read from the database via JDBC.
     *
     * @return the decrypting row mapper
     */
    public RowMapper<OAuth2Authorization> getRowMapper() {
        final var defaultRowMapper = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(
                registeredClientRepository
        );
        return (rs, rowNum) -> {
            final var authorization = defaultRowMapper.mapRow(rs, rowNum);

            final var builder = OAuth2Authorization.from(authorization);

            final var accessToken = authorization.getAccessToken();
            final var refreshToken = authorization.getRefreshToken();
            final var oidcIdToken = authorization.getToken(OidcIdToken.class);
            final var oauth2UserCode = authorization.getToken(OAuth2UserCode.class);
            final var oauth2DeviceCode = authorization.getToken(OAuth2DeviceCode.class);

            if (Objects.nonNull(accessToken)) {
                builder.accessToken(decrypt(accessToken.getToken()));
            }
            if (Objects.nonNull(refreshToken)) {
                builder.refreshToken(decrypt(refreshToken.getToken()));
            }
            if (Objects.nonNull(oidcIdToken)) {
                builder.token(
                        decrypt(oidcIdToken.getToken()),
                        c -> c.putAll(oidcIdToken.getMetadata())
                );
            }
            if (Objects.nonNull(oauth2UserCode)) {
                builder.token(
                        decrypt(oauth2UserCode.getToken()),
                        c -> c.putAll(oauth2UserCode.getMetadata())
                );
            }
            if (Objects.nonNull(oauth2DeviceCode)) {
                builder.token(
                        decrypt(oauth2DeviceCode.getToken()),
                        c -> c.putAll(oauth2DeviceCode.getMetadata())
                );
            }
            return builder.build();
        };
    }

    private OAuth2AccessToken encrypt(final OAuth2AccessToken accessToken) {
        final var cipherToken = encrypt(accessToken.getTokenValue());
        return new OAuth2AccessToken(
                accessToken.getTokenType(),
                cipherToken,
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt(),
                accessToken.getScopes()
        );
    }

    private OAuth2RefreshToken encrypt(final OAuth2RefreshToken refreshToken) {
        final var cipherToken = encrypt(refreshToken.getTokenValue());
        return new OAuth2RefreshToken(
                cipherToken,
                refreshToken.getIssuedAt(),
                refreshToken.getExpiresAt()
        );
    }

    private OidcIdToken encrypt(final OidcIdToken idToken) {
        final var cipherToken = encrypt(idToken.getTokenValue());
        return new OidcIdToken(
                cipherToken,
                idToken.getIssuedAt(),
                idToken.getExpiresAt(),
                idToken.getClaims()
        );
    }

    private OAuth2UserCode encrypt(final OAuth2UserCode userCode) {
        final var cipherToken = encrypt(userCode.getTokenValue());
        return new OAuth2UserCode(cipherToken, userCode.getIssuedAt(), userCode.getExpiresAt());
    }

    private OAuth2DeviceCode encrypt(final OAuth2DeviceCode deviceCode) {
        final var cipherToken = encrypt(deviceCode.getTokenValue());
        return new OAuth2DeviceCode(cipherToken, deviceCode.getIssuedAt(), deviceCode.getExpiresAt());
    }

    private String encrypt(final String tokenValue) {
        return encryptor.encrypt(passwordVersion, tokenValue);
    }

    private OAuth2AccessToken decrypt(final OAuth2AccessToken accessToken) {
        final var cipherToken = decrypt(accessToken.getTokenValue());
        return new OAuth2AccessToken(
                accessToken.getTokenType(),
                cipherToken,
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt(),
                accessToken.getScopes()
        );
    }

    private OAuth2RefreshToken decrypt(final OAuth2RefreshToken refreshToken) {
        final var cipherToken = decrypt(refreshToken.getTokenValue());
        return new OAuth2RefreshToken(
                cipherToken,
                refreshToken.getIssuedAt(),
                refreshToken.getExpiresAt()
        );
    }

    private OidcIdToken decrypt(final OidcIdToken idToken) {
        final var cipherToken = decrypt(idToken.getTokenValue());
        return new OidcIdToken(
                cipherToken,
                idToken.getIssuedAt(),
                idToken.getExpiresAt(),
                idToken.getClaims()
        );
    }

    private OAuth2UserCode decrypt(final OAuth2UserCode userCode) {
        final var cipherToken = decrypt(userCode.getTokenValue());
        return new OAuth2UserCode(cipherToken, userCode.getIssuedAt(), userCode.getExpiresAt());
    }

    private OAuth2DeviceCode decrypt(final OAuth2DeviceCode deviceCode) {
        final var cipherToken = decrypt(deviceCode.getTokenValue());
        return new OAuth2DeviceCode(cipherToken, deviceCode.getIssuedAt(), deviceCode.getExpiresAt());
    }

    private String decrypt(final String tokenValue) {
        return encryptor.decrypt(tokenValue);
    }
}
