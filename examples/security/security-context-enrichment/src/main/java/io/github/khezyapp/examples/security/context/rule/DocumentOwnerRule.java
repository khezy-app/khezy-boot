package io.github.khezyapp.examples.security.context.rule;

import io.github.khezyapp.api.security.api.AuthorizationRule;
import io.github.khezyapp.api.security.expression.KhezySecurityExpressionRoot;
import io.github.khezyapp.examples.security.context.repository.DocumentRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
class DocumentOwnerRule implements AuthorizationRule {

    private final DocumentRepository documentRepository;

    DocumentOwnerRule(final DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public String getName() {
        return "DOCUMENT_OWNER";
    }

    @Override
    public boolean evaluate(final KhezySecurityExpressionRoot root,
                            final Object[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        final var docId = args[0].toString();
        final var doc = documentRepository.findById(docId);
        if (doc.isEmpty()) {
            return false;
        }
        final var username = root.getAuthentication().getName();
        final var tenantId = root.getSecurityAttributeContext()
                .getAdditionalAttributes()
                .get("tenantId");
        if (Objects.isNull(tenantId)) {
            return false;
        }
        return doc.get().ownerId().equals(username)
                && doc.get().tenantId().equals(tenantId.toString());
    }
}
