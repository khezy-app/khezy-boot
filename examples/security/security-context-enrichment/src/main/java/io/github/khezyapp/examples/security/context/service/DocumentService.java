package io.github.khezyapp.examples.security.context.service;

import io.github.khezyapp.api.security.annotation.RequiredAuthorizationRule;
import io.github.khezyapp.api.security.annotation.RequiredRole;
import io.github.khezyapp.examples.security.context.enity.Document;
import io.github.khezyapp.examples.security.context.repository.DocumentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository repository;

    DocumentService(final DocumentRepository repository) {
        this.repository = repository;
    }

    @RequiredAuthorizationRule(ruleName = "DOCUMENT_OWNER", params = "#id")
    public Optional<Document> findById(final String id) {
        return repository.findById(id);
    }

    @PreAuthorize("check('TENANT_MEMBER')")
    public List<Document> findAll() {
        return repository.findAll();
    }

    @RequiredRole(roles = {"'EDITOR'"})
    public Document create(final String title, final String tenantId, final String ownerId) {
        final var id = String.valueOf(repository.findAll().size() + 1);
        final var doc = new Document(id, title, ownerId, tenantId);
        return doc;
    }
}
