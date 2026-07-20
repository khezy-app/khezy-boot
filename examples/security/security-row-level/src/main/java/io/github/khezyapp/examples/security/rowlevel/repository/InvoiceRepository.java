package io.github.khezyapp.examples.security.rowlevel.repository;

import io.github.khezyapp.examples.security.rowlevel.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdWithQuery(@Param("id") Long id);

    List<Invoice> findByTenantId(String tenantId);
}
