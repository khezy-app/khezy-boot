package io.github.khezyapp.examples.security.context.repository;

import io.github.khezyapp.examples.security.context.enity.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentRepository {

    private final Map<String, Document> store = new ConcurrentHashMap<>();

    DocumentRepository() {
        store.put("1", new Document("1", "Design Doc", "alice", "acme"));
        store.put("2", new Document("2", "Meeting Notes", "bob", "acme"));
        store.put("3", new Document("3", "Secret Plan", "alice", "globex"));
        store.put("4", new Document("4", "Public Draft", "charlie", "acme"));
    }

    public Optional<Document> findById(final String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Document> findAll() {
        return new ArrayList<>(store.values());
    }

    public List<Document> findByTenantId(final String tenantId) {
        return store.values().stream()
                .filter(doc -> doc.tenantId().equals(tenantId))
                .toList();
    }
}
