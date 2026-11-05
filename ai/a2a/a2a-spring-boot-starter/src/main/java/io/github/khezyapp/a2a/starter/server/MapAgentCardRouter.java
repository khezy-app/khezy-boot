package io.github.khezyapp.a2a.starter.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.a2aproject.sdk.server.multitenancy.AgentCardRouter;
import org.a2aproject.sdk.spec.AgentCard;
import org.jspecify.annotations.Nullable;

/**
 * Simple tenant router over maps of {@link AgentCard} instances.
 *
 * <p>The default card is served for a blank or {@code null} tenant. A non-blank tenant
 * that has no registered card resolves to {@code null}, which the SDK and the card
 * controller treat as HTTP 404.</p>
 */
public final class MapAgentCardRouter implements AgentCardRouter {

    private final AgentCard defaultCard;
    private final Map<String, AgentCard> publicCards;
    private final Map<String, AgentCard> extendedCards;

    /**
     * Creates a router without extended cards.
     *
     * @param defaultCard the card served for a blank tenant
     * @param publicCards tenant-specific public cards keyed by tenant id
     */
    public MapAgentCardRouter(final AgentCard defaultCard, final Map<String, AgentCard> publicCards) {
        this(defaultCard, publicCards, Map.of());
    }

    /**
     * Creates a router.
     *
     * @param defaultCard the card served for a blank tenant
     * @param publicCards tenant-specific public cards keyed by tenant id
     * @param extendedCards tenant-specific extended cards keyed by tenant id
     */
    public MapAgentCardRouter(final AgentCard defaultCard, final Map<String, AgentCard> publicCards,
                              final Map<String, AgentCard> extendedCards) {
        this.defaultCard = Objects.requireNonNull(defaultCard, "defaultCard must not be null");
        this.publicCards = copy(publicCards);
        this.extendedCards = copy(extendedCards);
    }

    @Override
    public @Nullable AgentCard resolvePublicCard(final @Nullable String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return defaultCard;
        }
        return publicCards.get(tenant);
    }

    @Override
    public @Nullable AgentCard resolveExtendedCard(final @Nullable String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return extendedCards.get("");
        }
        return extendedCards.get(tenant);
    }

    private static Map<String, AgentCard> copy(final Map<String, AgentCard> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
