package io.github.khezyapp.examples.security.rowlevel.service;

import io.github.khezyapp.api.security.annotation.RowLevelSecurity;
import io.github.khezyapp.examples.security.rowlevel.entity.Invoice;
import io.github.khezyapp.examples.security.rowlevel.repository.InvoiceRepository;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(final InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @RowLevelSecurity(
            filterName = "tenantFilter",
            parameterName = "tenantId",
            expression = "#tenantId"
    )
    public List<Invoice> findAll(@P("tenantId") final String tenantId) {
        return invoiceRepository.findAll();
    }

    @RowLevelSecurity(
            filterName = "tenantFilter",
            parameterName = "tenantId",
            expression = "#tenantId"
    )
    public Optional<Invoice> findById(final Long id,
                                      @P("tenantId") final String tenantId) {
        return invoiceRepository.findByIdWithQuery(id);
    }

    @RowLevelSecurity(
            filterName = "tenantFilter",
            parameterName = "tenantId",
            expression = "#tenantId"
    )
    public BigDecimal getTotalAmount(@P("tenantId") final String tenantId) {
        return invoiceRepository.findAll().stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
