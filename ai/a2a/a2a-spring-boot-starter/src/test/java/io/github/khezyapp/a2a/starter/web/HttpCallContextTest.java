package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.executor.CallerIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCallContextTest {

    @Test
    @DisplayName("from() seeds requestUri, method and remoteAddr attributes and resolves identity")
    void shouldSeedAttributesFromRequest() {
        final var request = new MockHttpServletRequest("POST", "/message/send");
        request.setRemoteAddr("10.0.0.9");

        final var context = HttpCallContext.from(request, CallerIdentity::anonymous);

        assertThat(context.attributes())
                .containsEntry("requestUri", "/message/send")
                .containsEntry("method", "POST")
                .containsEntry("remoteAddr", "10.0.0.9");
        assertThat(context.caller()).isEqualTo(CallerIdentity.anonymous());
    }

    @Test
    @DisplayName("blank remoteAddr is omitted from the attributes snapshot")
    void shouldOmitBlankRemoteAddr() {
        final var request = new MockHttpServletRequest("GET", "/.well-known/agent-card.json");
        request.setRemoteAddr("");

        final var context = HttpCallContext.from(request, CallerIdentity::anonymous);

        assertThat(context.attributes()).doesNotContainKey("remoteAddr");
    }

    @Test
    @DisplayName("attributes are immutable and constructor rejects nulls")
    void shouldBeImmutableAndRejectNulls() {
        final var context = new HttpCallContext(CallerIdentity.anonymous(), Map.of("k", "v"));

        assertThatThrownBy(() -> context.attributes().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(context.attributes()).isEqualTo(Map.of("k", "v"));
        assertThatThrownBy(() -> new HttpCallContext(null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HttpCallContext(CallerIdentity.anonymous(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
