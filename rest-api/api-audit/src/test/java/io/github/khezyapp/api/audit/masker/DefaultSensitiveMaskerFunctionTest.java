package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultSensitiveMaskerFunctionTest {

    @Mock
    private SensitiveMaskerStrategy strategy;

    private DefaultSensitiveMasker masker;

    @BeforeEach
    void setUp() {
        masker = new DefaultSensitiveMasker(strategy);
    }

    @Test
    void shouldDelegateToStrategy() {
        // Given
        final var payload = "secret-data";
        final var expectedMask = "******";
        when(strategy.mask(eq(payload), any(SensitiveMaskerContext.class))).thenReturn(expectedMask);

        // When
        final var result = masker.mask(payload);

        // Then
        assertThat(result).isEqualTo(expectedMask);
        verify(strategy).mask(eq(payload), any(SensitiveMaskerContext.class));
    }
}
