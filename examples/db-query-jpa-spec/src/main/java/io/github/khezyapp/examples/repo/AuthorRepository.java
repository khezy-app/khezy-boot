package io.github.khezyapp.examples.repo;

import io.github.khezyapp.examples.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, Long> {
}
