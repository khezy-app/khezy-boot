package io.github.khezyapp.api.security.token.extractor;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerTokenExtractorTest {

    private final BearerTokenExtractor extractor = new BearerTokenExtractor();

    @Test
    void shouldExtractTokenFromBearerHeader() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer my-token-value");

        final var result = extractor.extract(request);

        assertThat(result).isEqualTo(Optional.of("my-token-value"));
    }

    @Test
    void shouldExtractTokenWithExtraSpaces() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer   my-token-value   ");

        final var result = extractor.extract(request);

        assertThat(result).isEqualTo(Optional.of("my-token-value"));
    }

    @Test
    void shouldReturnEmptyWhenNoAuthHeader() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        final var result = extractor.extract(request);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNotBearer() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        final var result = extractor.extract(request);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTokenIsOnlyBearer() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer");

        final var result = extractor.extract(request);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenBearerPrefixHasWrongCase() {
        final var request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("bearer my-token");

        final var result = extractor.extract(request);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnScheme() {
        assertThat(extractor.scheme()).isEqualTo("Bearer");
    }

    @Test
    void shouldThrowOnNullRequest() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> extractor.extract(null)
        );
    }
}
