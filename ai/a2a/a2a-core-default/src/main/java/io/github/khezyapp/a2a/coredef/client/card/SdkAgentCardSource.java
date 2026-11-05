package io.github.khezyapp.a2a.coredef.client.card;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpHeaders;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.util.Utils;

/**
 * Fetches agent cards over the SDK HTTP client.
 *
 * <p>The URL rules mirror the SDK 1.4 {@code A2ACardResolver}: raw {@code .json}
 * URLs, custom card paths, tenant paths, well-known suffix stripping, and the
 * fallback URL. Conditional request headers are supported so
 * {@link CachingAgentCardResolver} can revalidate without downloading the card.</p>
 */
public class SdkAgentCardSource implements AgentCardSource {

    private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*(\\d+)");

    private final A2AHttpClient httpClient;
    private final String cardPath;
    private final Map<String, String> authHeaders;
    private final String tenant;

    /**
     * Creates a source with the standard card path and no authentication.
     *
     * @param httpClient the shared HTTP client
     */
    public SdkAgentCardSource(final A2AHttpClient httpClient) {
        this(httpClient, null, Map.of(), null);
    }

    /**
     * Creates a source.
     *
     * @param httpClient the shared HTTP client
     * @param cardPath an optional non-standard card path
     * @param authHeaders headers sent with the card request
     * @param tenant an optional tenant path segment
     */
    public SdkAgentCardSource(final A2AHttpClient httpClient, final String cardPath,
                              final Map<String, String> authHeaders, final String tenant) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.cardPath = cardPath;
        this.authHeaders = copyHeaders(authHeaders);
        this.tenant = tenant;
    }

    @Override
    public CardFetch fetch(final String baseUrl, final CardCacheHeader cacheHeader) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return CardFetch.failed();
        }
        final CardCacheHeader validators = cacheHeader == null ? CardCacheHeader.empty() : cacheHeader;
        final ResolvedUrls urls;
        try {
            urls = resolveUrls(baseUrl);
        } catch (URISyntaxException | RuntimeException e) {
            return CardFetch.failed();
        }
        final var primary = fetchUrl(urls.cardUrl(), validators);
        if (primary.httpStatus() == 404 && urls.fallbackUrl() != null) {
            return fetchUrl(urls.fallbackUrl(), validators).fetch();
        }
        return primary.fetch();
    }

    private Attempt fetchUrl(final String url, final CardCacheHeader cacheHeader) {
        try {
            final var builder = httpClient.createGet()
                    .url(url)
                    .addHeader(A2AHttpClient.CONTENT_TYPE, A2AHttpClient.APPLICATION_JSON);
            if (!authHeaders.isEmpty()) {
                builder.addHeaders(authHeaders);
            }
            if (!cacheHeader.isEmpty()) {
                if (cacheHeader.etag() != null && !cacheHeader.etag().isBlank()) {
                    builder.addHeader("If-None-Match", cacheHeader.etag());
                }
                if (cacheHeader.lastModified() != null && !cacheHeader.lastModified().isBlank()) {
                    builder.addHeader("If-Modified-Since", cacheHeader.lastModified());
                }
            }
            final A2AHttpResponse response = builder.get();
            final int status = response.status();
            final A2AHttpHeaders headers = response.headers();
            final String etag = headers.firstValue("ETag");
            final String lastModified = headers.firstValue("Last-Modified");
            final Duration maxAge = parseMaxAge(headers.firstValue("Cache-Control"));
            if (status == 304) {
                return new Attempt(CardFetch.notModified(etag, lastModified, maxAge), status);
            }
            if (!response.success()) {
                return new Attempt(CardFetch.failed(), status);
            }
            return new Attempt(CardFetch.ok(parseCard(response.body()), etag, lastModified, maxAge), status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Attempt(CardFetch.failed(), 0);
        } catch (Exception e) {
            return new Attempt(CardFetch.failed(), 0);
        }
    }

    private ResolvedUrls resolveUrls(final String baseUrl) throws URISyntaxException {
        final String cleanBase = Utils.stripWellKnownSuffix(baseUrl);
        if (cardPath != null && !cardPath.isBlank()) {
            final String baseUrlWithTenant = Utils.buildBaseUrl(cleanBase, tenant);
            Utils.validateAbsoluteUrl(baseUrlWithTenant);
            return new ResolvedUrls(Utils.buildCardUrl(baseUrlWithTenant, cardPath), null);
        }
        final String cardUrl;
        if (tenant != null && !tenant.isBlank()) {
            Utils.validateTenant(tenant);
            Utils.validateAbsoluteUrl(cleanBase);
            cardUrl = Utils.buildCardUrl(cleanBase, Utils.buildTenantCardPath(tenant));
        } else {
            Utils.validateAbsoluteUrl(cleanBase);
            cardUrl = Utils.buildCardUrl(cleanBase, Utils.DEFAULT_AGENT_CARD_PATH);
        }
        final String fallbackUrl = isSameUrl(cardUrl, baseUrl) ? cleanBase : baseUrl;
        return new ResolvedUrls(cardUrl, fallbackUrl);
    }

    private static AgentCard parseCard(final String body) throws Exception {
        final var builder = org.a2aproject.sdk.grpc.AgentCard.newBuilder();
        JSONRPCUtils.parseJsonString(body, builder, "", true);
        return ProtoUtils.FromProto.agentCard(builder);
    }

    private static Duration parseMaxAge(final String cacheControl) {
        if (cacheControl == null || cacheControl.isBlank()) {
            return null;
        }
        final String lower = cacheControl.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("no-store") || lower.contains("no-cache")) {
            return Duration.ZERO;
        }
        final var matcher = MAX_AGE.matcher(lower);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isSameUrl(final String first, final String second) {
        final String normalFirst = first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
        final String normalSecond = second.endsWith("/") ? second.substring(0, second.length() - 1) : second;
        return normalFirst.equals(normalSecond);
    }

    private static Map<String, String> copyHeaders(final Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        final var copy = new LinkedHashMap<String, String>(source.size());
        copy.putAll(source);
        return Map.copyOf(copy);
    }

    private record ResolvedUrls(String cardUrl, String fallbackUrl) {
    }

    private record Attempt(CardFetch fetch, int httpStatus) {
    }
}
