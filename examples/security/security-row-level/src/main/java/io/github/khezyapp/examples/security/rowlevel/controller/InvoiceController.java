package io.github.khezyapp.examples.security.rowlevel.controller;

import io.github.khezyapp.examples.security.rowlevel.entity.Invoice;
import io.github.khezyapp.examples.security.rowlevel.service.InvoiceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(final InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping(value = "/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Invoice>> listInvoices(
            @RequestHeader("X-Tenant-Id") final String tenantId
    ) {
        return ResponseEntity.ok(invoiceService.findAll(tenantId));
    }

    @GetMapping(value = "/invoices/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Invoice> getInvoice(
            @PathVariable final Long id,
            @RequestHeader("X-Tenant-Id") final String tenantId
    ) {
        return invoiceService.findById(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/invoices/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader("X-Tenant-Id") final String tenantId
    ) {
        final var total = invoiceService.getTotalAmount(tenantId);
        return ResponseEntity.ok(Map.of("totalAmount", total));
    }
}
