package io.github.khezyapp.api.security.aop;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RowLevelSecurityPointcutTest {

    private final RowLevelSecurityPointcut pointcut = new RowLevelSecurityPointcut();

    @RowLevelSecurity(filterName = "tenantFilter", parameterName = "tenantId", expression = "'acme'")
    public void annotatedMethod() {
    }

    public void nonAnnotatedMethod() {
    }

    @Test
    void shouldMatchAnnotatedMethods() throws Exception {
        final var method = getClass().getMethod("annotatedMethod");
        assertThat(pointcut.matches(method, getClass())).isTrue();
    }

    @Test
    void shouldNotMatchUnannotatedMethods() throws Exception {
        final var method = getClass().getMethod("nonAnnotatedMethod");
        assertThat(pointcut.matches(method, getClass())).isFalse();
    }
}
