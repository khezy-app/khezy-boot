package io.github.khezyapp.a2a.testapp;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Host-application repository used to prove the store auto-configuration is additive.
 */
public interface AppNoteRepository extends JpaRepository<AppNote, Long> {
}
