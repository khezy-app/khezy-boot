package io.github.khezyapp.api.security.token;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface for parsing and validating raw tokens.
 * Implementations handle token verification, expiration checks,
 * and claim extraction.
 */
public interface TokenParser {

    /**
     * Parses and validates the raw token string.
     *
     * @param token the raw token to parse
     * @return the parsed token with claims and authorities
     * @throws TokenException if the token is invalid or expired
     */
    ParsedToken parse(String token);

    /**
     * Represents a successfully parsed token with its claims and granted authorities.
     *
     * @param subject           the token subject (typically user identifier)
     * @param claims            all claims from the token
     * @param grantedAuthorities authorities granted by the token
     */
    record ParsedToken(
            String subject,
            Map<String, Object> claims,
            List<String> grantedAuthorities
    ) {
    }

    /**
     * Thrown when a token cannot be parsed or validated.
     */
    final class TokenException extends RuntimeException {

        /**
         * Creates a token exception with the given message.
         *
         * @param message human-readable error description
         */
        public TokenException(final String message) {
            super(message);
        }

        /**
         * Creates a token exception with the given message and cause.
         *
         * @param message human-readable error description
         * @param cause   the underlying exception, may be null
         */
        public TokenException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
