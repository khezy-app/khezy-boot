package io.github.khezyapp.api.security.token.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimBasedFactorExtractorTest {

    private final ClaimBasedFactorExtractor extractor = new ClaimBasedFactorExtractor();

    @Test
    void shouldPrependFactorPrefix() {
        final Map<String, Object> claims = Map.of("factors", List.of("PASSWORD", "WEBAUTHN"));

        final var result = extractor.extractFactors(claims);

        assertThat(result).containsExactly("FACTOR_PASSWORD", "FACTOR_WEBAUTHN");
    }

    @Test
    void shouldHandleAlreadyPrefixedFactor() {
        final Map<String, Object> claims = Map.of("factors", List.of("FACTOR_PASSWORD"));

        final var result = extractor.extractFactors(claims);

        assertThat(result).containsExactly("FACTOR_PASSWORD");
    }

    @Test
    void shouldReturnEmptyForNullClaims() {
        final var result = extractor.extractFactors(null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForMissingClaim() {
        final Map<String, Object> claims = Map.of("other", "value");

        final var result = extractor.extractFactors(claims);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNonListClaim() {
        final Map<String, Object> claims = Map.of("factors", "not-a-list");

        final var result = extractor.extractFactors(claims);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipNullElements() {
        final var factors = new java.util.ArrayList<String>();
        factors.add("PASSWORD");
        factors.add(null);
        factors.add("SECRET_QUESTION");
        final Map<String, Object> claims = Map.of("factors", factors);

        final var result = extractor.extractFactors(claims);

        assertThat(result).containsExactly("FACTOR_PASSWORD", "FACTOR_SECRET_QUESTION");
    }

    @Test
    void shouldUseCustomClaimKey() {
        final var customExtractor = new ClaimBasedFactorExtractor("custom-factors");
        final Map<String, Object> claims = Map.of("custom-factors", List.of("PIN"));

        final var result = customExtractor.extractFactors(claims);

        assertThat(result).containsExactly("FACTOR_PIN");
    }

    @Test
    void shouldThrowForNullCustomClaimKey() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> new ClaimBasedFactorExtractor(null)
        );
    }
}
