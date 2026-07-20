package io.github.khezyapp.examples.security.context.rule;

import io.github.khezyapp.api.security.api.AuthorizationRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;
import io.github.khezyapp.examples.security.context.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
class TenantMemberRule implements AuthorizationRule {

    private final DocumentRepository documentRepository;

    TenantMemberRule(final DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public String getName() {
        return "TENANT_MEMBER";
    }

    @Override
    public boolean evaluate(final KhezySecurityExpressionRoot root, final Object[] args) {
        final var tenantId = root.getSecurityAttributeContext()
                .getAdditionalAttributes()
                .get("tenantId");
        if (Objects.isNull(tenantId)) {
            return false;
        }
        final var userTenant = tenantId.toString();
        return documentRepository.findByTenantId(userTenant).stream()
                .findAny()
                .isPresent();
    }
}
