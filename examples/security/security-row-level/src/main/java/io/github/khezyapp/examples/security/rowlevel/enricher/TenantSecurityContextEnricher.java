package io.github.khezyapp.examples.security.rowlevel.enricher;

import io.github.khezyapp.api.security.api.SecurityContextEnricher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Component
public class TenantSecurityContextEnricher implements SecurityContextEnricher {

    @Override
    public Map<String, Object> getAdditionalContext() {
        final var requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(requestAttributes)) {
            return Collections.emptyMap();
        }
        final var tenantId = requestAttributes.getRequest().getHeader("X-Tenant-Id");
        if (Objects.isNull(tenantId) || tenantId.isBlank()) {
            return Collections.emptyMap();
        }
        return Map.of("tenantId", tenantId);
    }
}
