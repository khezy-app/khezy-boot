package io.github.khezyapp.a2a.starter.web;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.spec.AgentCard;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.khezyapp.a2a.starter.A2AServerProperties;

/**
 * Serves the public agent card as proto-JSON with an {@code ETag} and a
 * {@code Cache-Control: max-age}, including tenant-aware cards.
 *
 * <p>Jackson serialization produces a card shape the SDK client rejects; the SDK proto
 * printer is used instead. A conditional request carrying a matching {@code If-None-Match}
 * receives {@code 304}; unknown tenants receive {@code 404}.</p>
 */
@RestController
@RequestMapping("${io.github.khezyapp.a2a.server.path:/a2a}")
public class AgentCardController {

    private final String defaultJson;
    private final String defaultEtag;
    private final CacheControl cacheControl;
    private final ObjectProvider<AgentCardRouter> cardRouters;

    /**
     * Creates the controller.
     *
     * @param properties the server properties
     * @param agentCard the default agent card
     * @param cardRouters the optional tenant card router
     */
    public AgentCardController(final A2AServerProperties properties, final AgentCard agentCard,
                               final ObjectProvider<AgentCardRouter> cardRouters) {
        Objects.requireNonNull(agentCard, "agentCard must not be null");
        this.defaultJson = toJson(agentCard);
        this.defaultEtag = etag(defaultJson);
        final var maxAge = properties.getCardCache().getMaxAge();
        this.cacheControl = CacheControl.maxAge(Objects.isNull(maxAge) ? 0L : Math.max(0L, maxAge.toSeconds()),
                TimeUnit.SECONDS);
        this.cardRouters = Objects.requireNonNull(cardRouters, "cardRouters must not be null");
    }

    /**
     * Returns the default public agent card.
     *
     * @param ifNoneMatch the conditional request validator
     * @return the card, or {@code 304}
     */
    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> agentCard(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) final String ifNoneMatch) {
        return respond(defaultJson, defaultEtag, ifNoneMatch);
    }

    /**
     * Returns a tenant-specific public agent card.
     *
     * @param tenant the tenant path segment
     * @param ifNoneMatch the conditional request validator
     * @return the card, {@code 304}, or {@code 404} for an unknown tenant
     */
    @GetMapping(path = "/.well-known/{tenant}/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tenantCard(@PathVariable("tenant") final String tenant,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) final String ifNoneMatch) {
        final var router = cardRouters.getIfUnique();
        if (router == null) {
            return ResponseEntity.notFound().build();
        }
        final AgentCard card = router.resolvePublicCard(tenant);
        if (card == null) {
            return ResponseEntity.notFound().build();
        }
        final var json = toJson(card);
        return respond(json, etag(json), ifNoneMatch);
    }

    private ResponseEntity<String> respond(final String json, final String etag, final @Nullable String ifNoneMatch) {
        if (ifNoneMatch != null && etag.equals(ifNoneMatch.strip())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * Serializes a card with the SDK proto-JSON printer.
     *
     * @param card the card
     * @return the proto-JSON body
     */
    public static String toJson(final AgentCard card) {
        Objects.requireNonNull(card, "card must not be null");
        try {
            return JsonFormat.printer()
                    .alwaysPrintFieldsWithNoPresence()
                    .omittingInsignificantWhitespace()
                    .print(ProtoUtils.ToProto.agentCard(card));
        } catch (final Exception e) {
            throw new IllegalStateException("Could not serialize the A2A agent card", e);
        }
    }

    /**
     * Builds a strong entity tag from the serialized card.
     *
     * @param json the serialized card
     * @return the quoted entity tag
     */
    public static String etag(final String json) {
        return "\"" + Integer.toHexString(json.hashCode()) + "\"";
    }
}
