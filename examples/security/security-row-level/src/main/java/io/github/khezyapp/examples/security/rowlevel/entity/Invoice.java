package io.github.khezyapp.examples.security.rowlevel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "invoices")
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = String.class)
)
@Filter(
        name = "tenantFilter",
        condition = "tenant_id = :tenantId"
)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    public Invoice() {
    }

    public Invoice(
            final String invoiceNumber,
            final String tenantId,
            final String customerName,
            final BigDecimal amount,
            final LocalDate issueDate
    ) {
        this.invoiceNumber = invoiceNumber;
        this.tenantId = tenantId;
        this.customerName = customerName;
        this.amount = amount;
        this.issueDate = issueDate;
    }

}
