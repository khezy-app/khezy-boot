package io.github.khezyapp.a2a.testapp;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Host-application entity used to prove the store auto-configuration is additive.
 */
@Entity
@Table(name = "app_notes")
public class AppNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;

    protected AppNote() {
    }

    /**
     * Creates a note with the supplied text.
     *
     * @param text the note text
     */
    public AppNote(final String text) {
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}
