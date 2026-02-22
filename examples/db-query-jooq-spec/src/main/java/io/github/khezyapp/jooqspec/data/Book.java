package io.github.khezyapp.jooqspec.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Book {
    private Long id;
    private String title;
    private double price;
    private Author author;
}
