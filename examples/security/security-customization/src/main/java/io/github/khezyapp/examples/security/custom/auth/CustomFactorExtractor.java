package io.github.khezyapp.examples.security.custom.auth;

import io.github.khezyapp.api.security.token.FactorExtractor;
import io.github.khezyapp.api.security.util.FactorAuthorities;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CustomFactorExtractor implements FactorExtractor {

    @Override
    public List<String> extractFactors(final Map<String, Object> claims) {
        if (Objects.isNull(claims)) {
            return Collections.emptyList();
        }
        final var raw = claims.get("mfa_claims");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(FactorAuthorities::getFactorAuthorityFromMethod)
                    .toList();
        }
        return Collections.emptyList();
    }
}
