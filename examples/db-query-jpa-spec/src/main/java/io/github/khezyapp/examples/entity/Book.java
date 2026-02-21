package io.github.khezyapp.examples.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private Double price;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private Author author;
}
