package io.github.khezyapp.api.audit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for resolving the original client IP address from an {@link HttpServletRequest}.
 * <p>
 * This class accounts for various proxy configurations and load balancers (such as Cloudflare, Nginx,
 * or standard HTTP proxies) that may obscure the actual client address by populating specific
 * HTTP headers. It prioritizes known proxy headers before falling back to the direct
 * remote address.
 * </p>
 */
public final class ClientIpUtil {

    /**
     * A prioritized list of HTTP headers commonly used by proxies and load balancers
     * to transmit the original client IP address.
     */
    private static final List<String> IP_HEADERS = Arrays.asList(
            "CF-Connecting-IP", // Cloudflare
            "X-Forwarded-For",  // Standard proxy header
            "X-Real-IP",        // Nginx proxy header
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ClientIpUtil() {
    }

    /**
     * Resolves the client's IP address by inspecting the request headers.
     * <p>
     * The method iterates through a predefined list of headers. If a valid IP is found
     * in a header like {@code X-Forwarded-For} which contains multiple comma-separated values,
     * only the first (original) entry is returned. If no valid header is found, it
     * falls back to {@link HttpServletRequest#getRemoteAddr()}.
     * </p>
     *
     * @param request the current HTTP servlet request
     * @return the resolved client IP address as a {@link String}
     */
    public static String getClientIp(final HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // X-Forwarded-For can be a comma-separated list; the first one is the client
                return ip.split(",")[0].strip();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Validates if a retrieved header value contains a plausible IP string.
     * <p>
     * A value is considered invalid if it is null, empty, or literally "unknown"
     * (a common placeholder used by some proxy servers).
     * </p>
     *
     * @param ip the IP string to validate
     * @return {@code true} if the string is a potentially valid IP, {@code false} otherwise
     */
    private static boolean isValidIp(final String ip) {
        return Objects.nonNull(ip) && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }
}
