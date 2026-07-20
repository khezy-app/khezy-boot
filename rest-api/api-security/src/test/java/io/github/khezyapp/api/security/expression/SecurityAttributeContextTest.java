package io.github.khezyapp.api.security.expression;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAttributeContextTest {

    @Test
    void shouldBuildWithAllFields() {
        final var attrs = Map.<String, Object>of("key", "value");
        final var ctx = SecurityAttributeContext.builder()
                .request(null)
                .additionalAttributes(attrs)
                .build();

        assertThat(ctx.getAdditionalAttributes()).isEqualTo(attrs);
        assertThat(ctx.getRequest()).isNull();
    }

    @Test
    void shouldSetAndGetAdditionalAttributes() {
        final var ctx = new SecurityAttributeContext();
        final var attrs = Map.<String, Object>of("tenant", "acme");
        ctx.setAdditionalAttributes(attrs);

        assertThat(ctx.getAdditionalAttributes()).isEqualTo(attrs);
    }

    @Test
    void shouldAllowNullAdditionalAttributes() {
        final var ctx = SecurityAttributeContext.builder().build();
        assertThat(ctx.getAdditionalAttributes()).isNull();
    }
}
