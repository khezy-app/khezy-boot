package io.github.khezyapp.examples.security.rowlevel.repository;

import io.github.khezyapp.examples.security.rowlevel.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
