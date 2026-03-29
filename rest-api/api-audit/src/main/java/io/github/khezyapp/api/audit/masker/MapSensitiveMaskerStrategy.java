package io.github.khezyapp.api.audit.masker;

import io.github.khezyapp.api.audit.api.SensitiveMaskerContext;
import io.github.khezyapp.api.audit.api.SensitiveMaskerStrategy;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SensitiveMaskerStrategy} designed to process {@link Map} structures.
 * <p>
 * This strategy identifies sensitive data within a map by comparing keys against a
 * predefined or custom set of {@link KeyValueMask} rules. It supports masking common
 * security, identity, financial, and contact information patterns while recursively
 * processing nested complex objects.
 * </p>
 */
public class MapSensitiveMaskerStrategy implements SensitiveMaskerStrategy {

    /**
     * A default registry of common sensitive keys (e.g., password, ssn, creditCard)
     * associated with their respective masking or ignore rules.
     */
    private static final Map<String, KeyValueMask> DEFAULT_KEYS_MAP = Map.ofEntries(
            // --- Security & Authentication (Mostly Ignore) ---
            Map.entry("password", KeyValueMask.builder().key("password").mask("******").build()),
            Map.entry("passphrase", KeyValueMask.builder().key("passphrase").mask("******").build()),
            Map.entry("secret", KeyValueMask.builder().key("secret").mask("******").build()),
            Map.entry("client_secret", KeyValueMask.builder().key("client_secret").ignore(true).build()),
            Map.entry("authorization", KeyValueMask.builder().key("authorization").ignore(true).build()),
            Map.entry("access_token", KeyValueMask.builder().key("access_token").ignore(true).build()),
            Map.entry("accessToken", KeyValueMask.builder().key("accessToken").ignore(true).build()),
            Map.entry("refresh_token", KeyValueMask.builder().key("refresh_token").ignore(true).build()),
            Map.entry("refreshToken", KeyValueMask.builder().key("refreshToken").ignore(true).build()),
            Map.entry("api_key", KeyValueMask.builder().key("api_key").ignore(true).build()),
            Map.entry("apiKey", KeyValueMask.builder().key("apiKey").ignore(true).build()),

            // --- Government & Identity (PII) ---
            Map.entry("ssn", KeyValueMask.builder().key("ssn").mask("***-**-****").build()),
            Map.entry("social_security", KeyValueMask.builder().key("social_security").mask("***-**-****").build()),
            Map.entry("socialSecurity", KeyValueMask.builder().key("socialSecurity").mask("***-**-****").build()),
            Map.entry("tax_id", KeyValueMask.builder().key("tax_id").mask("******").build()),
            Map.entry("taxId", KeyValueMask.builder().key("taxId").mask("******").build()),
            Map.entry("passport", KeyValueMask.builder().key("passport").mask("******").build()),
            Map.entry("driver_license", KeyValueMask.builder().key("driver_license").mask("******").build()),
            Map.entry("driverLicense", KeyValueMask.builder().key("driverLicense").mask("******").build()),

            // --- Financial & Payment (PCI) ---
            Map.entry("credit_card", KeyValueMask.builder().key("credit_card").mask("****-****-****-****").build()),
            Map.entry("creditCard", KeyValueMask.builder().key("creditCard").mask("****-****-****-****").build()),
            Map.entry("cardNumber", KeyValueMask.builder().key("cardNumber").mask("****-****-****-****").build()),
            Map.entry("cvv", KeyValueMask.builder().key("cvv").ignore(true).build()),
            Map.entry("cvc", KeyValueMask.builder().key("cvc").ignore(true).build()),
            Map.entry("pin", KeyValueMask.builder().key("pin").ignore(true).build()),
            Map.entry("bank_account", KeyValueMask.builder().key("bank_account").mask("******").build()),

            // --- Personal Contact Info (Optional Masking) ---
            Map.entry("phone", KeyValueMask.builder().key("phone").mask("*******").build()),
            Map.entry("phoneNumber", KeyValueMask.builder().key("phoneNumber").mask("*******").build()),
            Map.entry("mobile", KeyValueMask.builder().key("mobile").mask("*******").build()),
            Map.entry("email", KeyValueMask.builder().key("email").mask("******@****.com").build())
    );

    private final Map<String, KeyValueMask> keyValueMasks;

    /**
     * Constructs a strategy instance using the default global masking rules.
     */
    public MapSensitiveMaskerStrategy() {
        this.keyValueMasks = DEFAULT_KEYS_MAP;
    }

    /**
     * Constructs a strategy instance with a custom list of masking rules.
     *
     * @param keyValueMasks a list of custom key-based masking configurations
     */
    public MapSensitiveMaskerStrategy(final List<KeyValueMask> keyValueMasks) {
        Assert.notNull(keyValueMasks, "keyValueMasks must not be empty");
        Assert.noNullElements(keyValueMasks, "keyValueMasks must not contain null elements");
        this.keyValueMasks = keyValueMasks.stream()
                .collect(Collectors.toMap(KeyValueMask::getKey, Function.identity()));
    }

    /**
     * Determines if the payload is an instance of {@link Map}.
     *
     * @param payload the object to check
     * @return {@code true} if the payload is a map; {@code false} otherwise
     */
    @Override
    public boolean supports(final Object payload) {
        return isMap(payload);
    }

    /**
     * Transforms the map by applying masking rules to specific keys and
     * recursively processing complex values.
     * <p>
     * Logic flow:
     * <ul>
     * <li>If a key exists in the masking registry, apply the mask or check for ignore status.</li>
     * <li>If the value is a primitive or null, keep the value as is.</li>
     * <li>If the value is complex, delegate to the context for recursive masking.</li>
     * </ul>
     * </p>
     *
     * @param payload the map instance to mask
     * @param context the current masking context for recursion and visitor tracking
     * @return a new map containing masked or processed entries
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object mask(final Object payload,
                       final SensitiveMaskerContext context) {
        if (payload instanceof Map<?, ?> map) {
            final var proceedMap = new HashMap<String, Object>((Map<? extends String, ?>) map);

            context.registerVisited(payload, proceedMap);

            final var keys = proceedMap.keySet();
            for (final String key : keys) {
                final var valueToMask = proceedMap.get(key);
                if (keyValueMasks.containsKey(key)) {
                    final var mask = keyValueMasks.get(key);
                    if (!mask.ignore) {
                        proceedMap.put(key, mask.mask);
                    }
                } else if (Objects.isNull(valueToMask) ||
                        isPrimitive(valueToMask.getClass())) {
                    proceedMap.put(key, valueToMask);
                } else {
                    final var maks = context.processMask(proceedMap.get(key));
                    proceedMap.put(key, maks);
                }
            }
            return proceedMap;
        }
        return payload;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 2;
    }

    /**
     * Data Transfer Object (DTO) representing a masking rule for a specific map key.
     */
    @Getter
    @Setter
    @Builder
    public static class KeyValueMask {
        private final String key;
        private final String mask;
        private final boolean ignore;
    }
}
