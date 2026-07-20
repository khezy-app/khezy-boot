package io.github.khezyapp.examples.security.rowlevel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    public Customer() {
    }

    public Customer(final String name,
                    final String tenantId) {
        this.name = name;
        this.tenantId = tenantId;
    }

}
