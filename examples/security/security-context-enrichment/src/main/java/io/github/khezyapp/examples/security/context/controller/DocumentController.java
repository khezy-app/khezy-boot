package io.github.khezyapp.examples.security.context.controller;

import io.github.khezyapp.examples.security.context.enity.Document;
import io.github.khezyapp.examples.security.context.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class DocumentController {

    private final DocumentService documentService;

    DocumentController(final DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping(value = "/documents/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Document> getDocument(@PathVariable final String id) {
        return documentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<Document>> listDocuments() {
        return ResponseEntity.ok(documentService.findAll());
    }
}
